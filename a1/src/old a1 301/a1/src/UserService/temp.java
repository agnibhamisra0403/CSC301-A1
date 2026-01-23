package UserService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * The UserService class implements a simple RESTful HTTP server for managing User entities.
 * <p>
 * It utilizes {@link com.sun.net.httpserver.HttpServer} to handle HTTP requests.
 * The service supports Create, Read, Update, and Delete (CRUD) operations via
 * GET and POST requests. Data is stored in an in-memory HashMap.
 * </p>
 */
public class temp {

    /**
     * In-memory database to store User objects.
     * <p>
     * A HashMap is used here as the execution model is single-threaded
     * (server.setExecutor(null)). If multi-threading is introduced,
     * this should be changed to a ConcurrentHashMap.
     * </p>
     */
    private static final Map<Integer, User> userDatabase = new HashMap<>();

    /**
     * The main entry point for the User Service.
     * <p>
     * Loads configuration, initializes the HTTP server, registers the context handler,
     * and starts the service.
     * </p>
     *
     * @param args Command line arguments. Expects the config file path as the first argument.
     * If not provided, defaults to "../config.json".
     * @throws IOException If the server fails to start or config cannot be read.
     */
    public static void main(String[] args) throws IOException {
        // 1. Load Configuration
        String configFilePath = args.length > 0 ? args[0] : "../config.json";
        int port = getPortFromConfig(configFilePath);
        System.out.println("Starting User Service on port: " + port);

        // 2. Start HTTP Server
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Set up context for /user request (GET and POST)
        server.createContext("/user", new UserHandler());

        // Use default executor (single-threaded) as seen in Main-2.java
        server.setExecutor(null); 

        server.start();
        System.out.println("User Service started.");
    }

    // ---------------------------------------------------------
    // Handler Implementation
    // ---------------------------------------------------------

    /**
     * UserHandler handles incoming HTTP requests for the "/user" context.
     * It routes requests to specific methods based on the HTTP Method (GET/POST).
     */
    static class UserHandler implements HttpHandler {

        /**
         * Handles the HTTP exchange.
         * * @param exchange The HttpExchange object encapsulating the request and response.
         * @throws IOException If an I/O error occurs during request handling.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("Received " + method + " request for " + path);

            try {
                // Route: /user/<id> (GET)
                if ("GET".equals(method) && path.matches("/user/\\d+")) {
                    handleGet(exchange, path);
                } 
                // Route: /user (POST)
                else if ("POST".equals(method) && path.equals("/user")) {
                    handlePost(exchange);
                } 
                else {
                    // Send 404 for invalid paths or 405 for invalid methods
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }

        /**
         * Handles GET requests to retrieve a specific user by ID.
         *
         * @param exchange The HttpExchange object.
         * @param path The request URI path containing the user ID.
         * @throws IOException If an I/O error occurs.
         */
        private void handleGet(HttpExchange exchange, String path) throws IOException {
            // Extract ID from URL /user/<id>
            String idStr = path.substring(path.lastIndexOf("/") + 1);
            int id = Integer.parseInt(idStr);

            if (userDatabase.containsKey(id)) {
                User user = userDatabase.get(id);
                sendResponse(exchange, 200, user.toJson());
            } else {
                sendErrorResponse(exchange, 404, "User Not Found"); //
            }
        }

        /**
         * Handles POST requests. Parses the JSON body and dispatches to 
         * create, update, or delete handlers based on the "command" field.
         *
         * @param exchange The HttpExchange object.
         * @throws IOException If an I/O error occurs.
         */
        private void handlePost(HttpExchange exchange) throws IOException {
            // Use the helper method from Main-2.java to read the body
            String requestBody = getRequestBody(exchange);
            
            // Manual JSON parsing
            Map<String, String> data = parseJson(requestBody);
            String command = data.get("command");

            if (command == null) {
                sendErrorResponse(exchange, 400, "Missing command");
                return;
            }

            switch (command) {
                case "create":
                    handleCreate(exchange, data);
                    break;
                case "update":
                    handleUpdate(exchange, data);
                    break;
                case "delete":
                    handleDelete(exchange, data);
                    break;
                default:
                    sendErrorResponse(exchange, 400, "Invalid Command"); //
            }
        }

        /**
         * Logic to create a new user.
         * Requires: id, username, email, password.
         *
         * @param exchange The HttpExchange object.
         * @param data The parsed JSON data map.
         * @throws IOException If an I/O error occurs.
         */
        private void handleCreate(HttpExchange exchange, Map<String, String> data) throws IOException {
            // Validation: All fields required
            if (!data.containsKey("id") || !data.containsKey("username") || 
                !data.containsKey("email") || !data.containsKey("password")) {
                sendErrorResponse(exchange, 400, "Missing fields");
                return;
            }

            int id = Integer.parseInt(data.get("id"));
            // Validation: Duplicate ID check
            if (userDatabase.containsKey(id)) {
                sendErrorResponse(exchange, 409, "User already exists");
                return;
            }

            String passwordHash = hashPassword(data.get("password"));
            User newUser = new User(id, data.get("username"), data.get("email"), passwordHash);
            userDatabase.put(id, newUser);

            // Success: 200 OK (not 201)
            sendResponse(exchange, 200, newUser.toJson());
        }

        /**
         * Logic to update an existing user.
         * Requires: id. Optional: username, email, password.
         *
         * @param exchange The HttpExchange object.
         * @param data The parsed JSON data map.
         * @throws IOException If an I/O error occurs.
         */
        private void handleUpdate(HttpExchange exchange, Map<String, String> data) throws IOException {
            // Validation: ID required
            if (!data.containsKey("id")) {
                sendErrorResponse(exchange, 400, "Missing ID");
                return;
            }

            int id = Integer.parseInt(data.get("id"));
            
            if (!userDatabase.containsKey(id)) {
                sendErrorResponse(exchange, 404, "User not found");
                return;
            }

            User user = userDatabase.get(id);

            // Update provided fields
            if (data.containsKey("username")) user.username = data.get("username");
            if (data.containsKey("email")) user.email = data.get("email");
            if (data.containsKey("password")) user.passwordHash = hashPassword(data.get("password"));

            sendResponse(exchange, 200, user.toJson());
        }

        /**
         * Logic to delete a user.
         * Requires authentication via password matching.
         *
         * @param exchange The HttpExchange object.
         * @param data The parsed JSON data map.
         * @throws IOException If an I/O error occurs.
         */
        private void handleDelete(HttpExchange exchange, Map<String, String> data) throws IOException {
            // Validation: All fields required
            if (!data.containsKey("id") || !data.containsKey("username") || 
                !data.containsKey("email") || !data.containsKey("password")) {
                sendErrorResponse(exchange, 400, "Missing fields for delete");
                return;
            }

            int id = Integer.parseInt(data.get("id"));
            if (!userDatabase.containsKey(id)) {
                sendErrorResponse(exchange, 404, "User not found");
                return;
            }

            User user = userDatabase.get(id);
            String inputHash = hashPassword(data.get("password"));

            // Auth: Validate credentials match
            if (user.username.equals(data.get("username")) && 
                user.email.equals(data.get("email")) && 
                user.passwordHash.equals(inputHash)) {
                
                userDatabase.remove(id);
                sendResponse(exchange, 200, ""); //
            } else {
                sendErrorResponse(exchange, 401, "Authentication failed"); //
            }
        }
    }

    // ---------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------

    /**
     * Reads the entire request body from the InputStream.
     *
     * @param exchange The HttpExchange object.
     * @return The request body as a String.
     * @throws IOException If an I/O error occurs.
     */
    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                requestBody.append(line);
            }
            return requestBody.toString();
        }
    }

    /**
     * Helper to send an HTTP response.
     *
     * @param exchange The HttpExchange object.
     * @param statusCode The HTTP status code (e.g., 200, 404).
     * @param response The response body string.
     * @throws IOException If an I/O error occurs.
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
    
    /**
     * Helper to send formatted error responses.
     * <p>
     * Returns an empty JSON object "{}" for specific error codes (400, 401, 404, 409)
     * as per the assignment specification, otherwise returns the message.
     * </p>
     *
     * @param exchange The HttpExchange object.
     * @param statusCode The HTTP status code.
     * @param message The internal error message (logged or debug use).
     * @throws IOException If an I/O error occurs.
     */
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        // For User/Product endpoints, invalid payload returns {}
        if (statusCode == 400 || statusCode == 409 || statusCode == 404 || statusCode == 401) {
             sendResponse(exchange, statusCode, "{}");
        } else {
             sendResponse(exchange, statusCode, message);
        }
    }

    // ---------------------------------------------------------
    // Utilities (Config, JSON, Hashing)
    // ---------------------------------------------------------

    /**
     * Simple Data Transfer Object representing a User.
     */
    static class User {
        int id;
        String username, email, passwordHash;
        
        public User(int id, String u, String e, String p) { this.id = id; this.username = u; this.email = e; this.passwordHash = p; }
        
        /**
         * serializes the User object to a JSON string.
         * @return JSON string representation.
         */
        public String toJson() {
            return String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", id, username, email, passwordHash);
        }
    }

    /**
     * Manually parses a flat JSON string into a Map.
     * <p>
     * Note: This is a simplified parser and does not support nested JSON objects or arrays.
     * It relies on string splitting by comma and colon.
     * </p>
     *
     * @param json The JSON string to parse.
     * @return A Map containing key-value pairs from the JSON.
     */
    private static Map<String, String> parseJson(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length >= 2) {
                String key = keyValue[0].trim().replaceAll("\"", "");
                String value = pair.substring(pair.indexOf(":") + 1).trim().replaceAll("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Hashes a password using SHA-256 algorithm.
     *
     * @param password The plain text password.
     * @return The hexadecimal string representation of the hashed password.
     * @throws RuntimeException If the SHA-256 algorithm is not available.
     */
    private static String hashPassword(String password) {
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
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /**
     * Reads the configuration file to find the port number for the UserService.
     *
     * @param filename The path to the configuration file (usually config.json).
     * @return The port number if found, otherwise defaults to 14001.
     */
    private static int getPortFromConfig(String filename) {
        try (Scanner scanner = new Scanner(new File(filename))) {
            StringBuilder json = new StringBuilder();
            while (scanner.hasNextLine()) json.append(scanner.nextLine());
            String content = json.toString();
            int userBlockStart = content.indexOf("\"UserService\"");
            if (userBlockStart == -1) return 14001;
            int portIndex = content.indexOf("\"port\"", userBlockStart);
            int colonIndex = content.indexOf(":", portIndex);
            int commaIndex = content.indexOf(",", colonIndex);
            int braceIndex = content.indexOf("}", colonIndex);
            int endIndex = (commaIndex == -1 || (braceIndex != -1 && braceIndex < commaIndex)) ? braceIndex : commaIndex;
            return Integer.parseInt(content.substring(colonIndex + 1, endIndex).trim());
        } catch (Exception e) { return 14001; }
    }
}