import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class temp
 {

    // In-Memory Database: Key = User ID, Value = JSON String of User Data
    private static final Map<Integer, String> userDb = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java UserService <config.json>");
            System.exit(1);
        }

        // 1. Read Config
        String configContent = new String(Files.readAllBytes(Paths.get(args[0])));
        int port = JsonUtils.getServicePort(configContent, "UserService");
        String ip = JsonUtils.getServiceIp(configContent, "UserService");

        if (port == -1 || ip == null) {
            System.err.println("Error: Could not parse UserService config.");
            System.exit(1);
        }

        // 2. Start Server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        server.createContext("/user", new UserHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("UserService started on " + ip + ":" + port);
    }

    static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                String method = t.getRequestMethod();
                String path = t.getRequestURI().getPath(); 

                if (method.equalsIgnoreCase("GET")) {
                    handleGet(t, path);
                } else if (method.equalsIgnoreCase("POST")) {
                    handlePost(t);
                } else {
                    sendResponse(t, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(t, 500, "{\"status\": \"Internal Error\"}");
            }
        }

        private void handleGet(HttpExchange t, String path) throws IOException {
            // Path format: /user/<id>
            String[] parts = path.split("/");
            if (parts.length != 3) {
                sendResponse(t, 400, "{}");
                return;
            }

            try {
                int id = Integer.parseInt(parts[2]);
                if (userDb.containsKey(id)) {
                    sendResponse(t, 200, userDb.get(id));
                } else {
                    sendResponse(t, 404, "{}");
                }
            } catch (NumberFormatException e) {
                sendResponse(t, 400, "{}");
            }
        }

        private void handlePost(HttpExchange t) throws IOException {
            String body = new BufferedReader(new InputStreamReader(t.getRequestBody()))
                    .lines().collect(Collectors.joining("\n"));

            String command = JsonUtils.parseString(body, "command");
            Integer id = JsonUtils.parseInt(body, "id");

            if (command == null || id == null) {
                sendResponse(t, 400, "{}");
                return;
            }

            // --- CREATE ---
            if (command.equalsIgnoreCase("create")) {
                if (userDb.containsKey(id)) {
                    sendResponse(t, 409, "{\"status\": \"User already exists\"}");
                    return;
                }
                
                String username = JsonUtils.parseString(body, "username");
                String email = JsonUtils.parseString(body, "email");
                String password = JsonUtils.parseString(body, "password");

                if (username == null || email == null || password == null) {
                    sendResponse(t, 400, "{}");
                    return;
                }

                String hashedPassword = hashPassword(password);
                String userJson = String.format(
                    "{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}",
                    id, username, email, hashedPassword
                );

                userDb.put(id, userJson);
                sendResponse(t, 200, userJson);
            }
            // --- UPDATE ---
            else if (command.equalsIgnoreCase("update")) {
                if (!userDb.containsKey(id)) {
                    // Updating non-existent user behaves like 404 per generic REST principles
                    sendResponse(t, 404, "{}");
                    return;
                }

                String currentJson = userDb.get(id);
                
                String currentUsername = JsonUtils.parseString(currentJson, "username");
                String currentEmail = JsonUtils.parseString(currentJson, "email");
                String currentPassHash = JsonUtils.parseString(currentJson, "password");

                String newUsername = JsonUtils.parseString(body, "username");
                String newEmail = JsonUtils.parseString(body, "email");
                String newPassword = JsonUtils.parseString(body, "password");

                if (newUsername != null) currentUsername = newUsername;
                if (newEmail != null) currentEmail = newEmail;
                if (newPassword != null) currentPassHash = hashPassword(newPassword);

                String updatedJson = String.format(
                    "{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}",
                    id, currentUsername, currentEmail, currentPassHash
                );

                userDb.put(id, updatedJson);
                sendResponse(t, 200, updatedJson);
            }
            // --- DELETE ---
            else if (command.equalsIgnoreCase("delete")) {
                if (!userDb.containsKey(id)) {
                    sendResponse(t, 404, "{}");
                    return;
                }

                String storedJson = userDb.get(id);
                String storedUser = JsonUtils.parseString(storedJson, "username");
                String storedEmail = JsonUtils.parseString(storedJson, "email");
                String storedPassHash = JsonUtils.parseString(storedJson, "password");

                String reqUser = JsonUtils.parseString(body, "username");
                String reqEmail = JsonUtils.parseString(body, "email");
                String reqPass = JsonUtils.parseString(body, "password");
                
                // STRICT CHECK: All fields must match
                String reqPassHash = hashPassword(reqPass);

                if (storedUser.equals(reqUser) && storedEmail.equals(reqEmail) && storedPassHash.equals(reqPassHash)) {
                    userDb.remove(id);
                    sendResponse(t, 200, "{}");
                } else {
                    sendResponse(t, 401, "{\"status\": \"Unauthorized\"}");
                }
            } else {
                sendResponse(t, 400, "{\"status\": \"Unknown Command\"}");
            }
        }

        private void sendResponse(HttpExchange t, int code, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(code, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private String hashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : encodedhash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (Exception e) {
                return "error";
            }
        }
    }
}