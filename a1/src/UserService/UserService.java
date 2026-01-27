package UserService;

import Helpers.Helpers;
import UserService.temp.UserHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Class UserService to handle user(s) related operations. 
 * 
 * @author misraagn
*/
public class UserService {
    // memory database to store users
    private static final Map<Integer, String> userDataBase = new HashMap<>();

    /**
     * The main function for the UserService class
     * 
     * @param args the command line arguements
     * @throws IOException if there is an error while writing or reading
     */
    public static void main(String[] args) throws IOException {

        // check if there is a valid input parameter provided
        if (args.length < 1) {
            System.err.println("Need to provide Config file");
            System.exit(1);
        }

        // use the config file to get the user server's ip and port
        String config = new String(Files.readAllBytes(Paths.get(args[0])));
        String ip = Helpers.getIP(config, "UserService");
        int port = Helpers.getPort(config, "UserService");

        // check the validity of the results
        if (port == -1 || ip == null) {
            System.err.println("Could not parse the config file");
            System.exit(1);
        }

        // create and start the http server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        server.createContext("/user", new UserHandler());

        // no concurrency needed for A1 as seen on piazza (CHANGE IN A2)
        server.setExecutor(null);
        server.start();
        System.out.println("UserService started: " + ip + ":" + port);
    }

    /**
     * The http handler class for userService
     * 
     * @author Agnibha Misra 
     */
    public static class UserHandler implements HttpHandler {
        /**
         * the handle method
         * 
         * @param exchange the http exchange object for the request and the response
         * @throws IOException if there is an error in reading/writing 
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                // GET method
                if (method.equalsIgnoreCase("get")) {
                    get(exchange, path);
                }

                // POST method
                else if (method.equalsIgnoreCase("post")) {
                    post(exchange, path);
                }

                // Invalid method
                else {
                    // send a response back, invalid method as it is not get or post
                    byte[] bytes = "Invalid method".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(405, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // semd a response back, error happened
                byte[] bytes = "{\"status\": \"Internal Error\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        }

        /**
         * The GET method handler for the UserServices class
         * @param exchange the Exchange object for the request and the response
         * @param path the path for the request
         * @throws IOException if error on writing or reading
         */
        private void get (HttpExchange exchange, String path) throws IOException{

            // tokenize the path
            String[] tokens = path.split("/");
            if (tokens.length != 3) {
                // send a message back, empty case
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            } 

            try {
                int id = Integer.parseInt(tokens[2]);
                if (userDataBase.containsKey(id)) {
                    // send a message back, the user's information
                    byte[] bytes = userDataBase.get(id).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }
                else {
                    // send a message back, the user does not exists
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }
            } catch (NumberFormatException e) {
                // send a message back, empty case (number parsing error)
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        }

        /**
         * The POST method handler for the UserServices class
         * @param exchange the Exchange object for the request and the response
         * @param path the path for the request
         * @throws IOException if error on writing or reading
         */
        private void post(HttpExchange exchange, String path) throws IOException {
            // read the request body
            Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
            String body;
            if (scanner.hasNext()) {body = scanner.next();}
            else {body = "";}

            // parse the request body
            String command = Helpers.parseString(body, "command");
            Integer id = Helpers.parseInteger(body, "id");

            // check for valid outputs
            if (command == null || id == null) {
                // send a message back, empty case
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }

            // Delete case:
            if (command.equalsIgnoreCase("delete")) {

                // check if user is in the data base
                if (!userDataBase.containsKey(id)) {
                    // send a message back, the target user to delete doesn't exist it the database
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // get the user data from the database
                String userObject = userDataBase.get(id);
                String DBUsername = Helpers.parseString(userObject, "username");
                String DBEmail = Helpers.parseString(userObject, "email");
                String DBHashedPassword = Helpers.parseString(userObject, "password"); 

                // get the user data from the request
                String requestUsername = Helpers.parseString(body, "username");
                String requestEmail = Helpers.parseString(body, "email");
                String requestPassword = Helpers.parseString(body, "password");

                String requestHashedPassword = passwordHasher(requestPassword);

                // check for any mismatch
                if (!DBEmail.equals(requestEmail) || !DBHashedPassword.equals(requestHashedPassword) || !DBUsername.equals(requestUsername)) {
                    byte[] bytes = "{\"status\": \"Cannot delete (USER details do not match DataBase\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }

                // can delete safely
                else {
                    // send the success message
                    userDataBase.remove(id);
                    byte[] bytes = "{\"status\": \"Success\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }

            }

            // Create case:
            else if (command.equalsIgnoreCase("create")) {
                
                // check whether the user already is in the database
                if (userDataBase.containsKey(id)) {
                    byte[] bytes = "{\"status\": \"User already in database\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(409, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                String email = Helpers.parseString(body, "email");
                String username = Helpers.parseString(body, "username");
                String password = Helpers.parseString(body, "password");

                if (username == null || email == null || password == null) {
                    // send message back, empty case (failed parsing or bad request input)
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // create the new user JSON object
                String hashedPW = passwordHasher(password);

                // check if the AI generated hash function actually outputted a valid hash
                if (hashedPW == null) {
                    // send response based on the failure
                    byte[] bytes = "{\"status\": \"Error during password hashing\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                String userObject = String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", id, username, email, hashedPW);

                // put the user JSON obect into the data base and send a success message
                userDataBase.put(id, userObject);
                byte[] bytes = "{\"status\": \"Success\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }

            // Update case:
            else if (command.equalsIgnoreCase("update")) {

                // make sure the user exists in the database
                if (!userDataBase.containsKey(id)) {
                    // user does not exist in the database
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // pull the object from the databse
                String userObject = userDataBase.get(id);
                
                // get the non-updated metadata of the object
                String email = Helpers.parseString(userObject, "email");
                String username = Helpers.parseString(userObject, "username");
                String password = Helpers.parseString(userObject, "password");

                // get the updated metadata of the object
                String updatedEmail = Helpers.parseString(body, "email");
                String updatedUsername = Helpers.parseString(body, "username");
                String updatedPassword = Helpers.parseString(body, "password");

                // check if the parameters need updating, if so then update
                if (updatedEmail != null) {email = updatedEmail;} 
                if (updatedUsername != null) {username = updatedUsername;}
                if (updatedPassword != null) {password = passwordHasher(updatedPassword);}

                // check if the AI generated hash function actually outputted a valid hash
                if (password == null) {
                    // send response based on the failure
                    byte[] bytes = "{\"status\": \"Error during password hashing\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // create the new user JSON object and place it into the database
                String updatedUserObject = String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", id, username, email, password);
                userDataBase.put(id, updatedUserObject);

                // send the success response back
                byte[] bytes = "{\"status\": \"Success\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }

            // Invalid command case:
            else {
                // 
                byte[] bytes = "{\"status\": \"Invalid Command\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }

        }

        /**
         * Crearte a hashed password based on the SHA-256 algorithm
         * 
         * This function was Mostly AI-generate with the following prompt to Gemini:
         * 
         * "Create me a function in java without ant external libraries that takes in
         * a String password and uses the SHA-256 algorithm to hash it and return 
         * the String output."
         * 
         * 
         * @param pw unhased password
         * @return hashed password
         */
        private String passwordHasher (String pw) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(pw.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexString = new StringBuilder();
                for (byte b : encodedhash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) hexString.append('0');
                    hexString.append(hex);
                }
                return hexString.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
