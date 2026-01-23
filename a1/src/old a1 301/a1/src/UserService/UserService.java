package UserService;

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
import java.util.concurrent.Executors;
import java.util.regex.*;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
 

/**
 * Class UserService to handle user(s) related operations. 
 * 
 * @author misraagn
*/
public class UserService {

    //memory to store users
    private static Map<Integer, User> userDatabase = new HashMap<>();

    public static void main(String[] args) throws IOException{
        // get config file from command line arg or use the default config.json
        String configPath;
        if (args.length > 0) {
            configPath = args[0];
        }
        else {
            // current working directory is a1
            configPath = "config.json";
        }

        // read the port number from the config file
        int port = 0;
        try {
            port = getConfigPort(configPath);
            System.out.println("Port for UserService: " + port);
        } catch (Exception e) {
            System.err.println("Error reading config: " + e.getMessage());
            e.printStackTrace();
        }

        // start HTTP server on the port
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/user", new UserHandler());
        
        // although not happening concurrently in A1, piazza advised to leave it as 20 so it is not forgotten later
        server.setExecutor(Executors.newFixedThreadPool(20));

        server.start(); //start the server
        
        System.out.println("Server started on port " + port);
    }

    /**
     * class UserHandler to handle user related requests
     * 
     * @author misraagn
     */
    static class UserHandler implements HttpHandler {
        @Override

        /**
         * handle method to deal with incoming HTTP requests
         * 
         * @param exchange the HttpExchange object for the request and appropriate response
         * @throws IOException if an I/O error occurs
         */
        public void handle(HttpExchange exchange) throws IOException {
            // handle the user request
            String requestMethod = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("Received method: " + requestMethod);
            System.out.println("Received path: " + path);
            
            try {
                if (path.matches("/user/\\d+") && "GET".equals(requestMethod)) {
                    handleGet(exchange, path);
                }
    
                else if (path.equals("/User") && "POST".equals(requestMethod)) {
                    handlePost(exchange);
                }
    
                else {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            } 
            catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        }

        /**
         * Method to handle the GET request for a user made through UserID
         * @param exchange the HttpExchange object
         * @param path the path which contains the UserID
         * @throws IOException if an I/O error occurs
         */
        private void handleGet(HttpExchange exchange, String path) throws IOException {
            int userID = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            if (userDatabase.containsKey(Userid)) {
                User user = userDatabase.get(userID);
            }
            else {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
            
        }

        /**
         * Method used to handle the POST request for user requests
         * 
         * @param exchange the HttpExchange object for the request and response
         * @throws IOException if an I/O error occurs
         */
        private void handlePost(HttpExchange exchange) throws IOException {

            //get the request body
            String requestBody = getRequestBody(exchange);

            // hashmap of the requesting user's data
            Map<String, String> userData = new HashMap<>();
            String requestBody1 = requestBody.trim(); // a copy for the sake of modification

            // remove the starting braces
            if (requestBody1.startsWith("{")) {
                requestBody1 = requestBody1.substring(1);
            }

            // remove the ending braces
            if (requestBody1.endsWith("}")) {
                requestBody1 = requestBody1.substring(0, requestBody1.length() - 1);
            }

            // split the string into a list based on comma separation
            String[] pairs = requestBody1.split(",");

            // loop through all the pairs and split based on colon
            for (String pair : pairs) {
                String [] keyValue = pair.split(":");
                if (keyValue.length >= 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String val = pair.substring(pair.indexOf(":") + 1).trim().replaceAll("\"", "");
                    userData.put(key, val);
                }

            }

            // get the command that the user performs
            String cmd = userData.get("command");

            if (cmd != null) {
                if (cmd.equals("create")) {
                    handleCreate(exchange, userData);
                }
                else if (cmd.equals("update")) {
                    handleUpdate(exchange, userData);
                }
                else if (cmd.equals("delete")){
                    handleDelete(exchange, userData);
                }
                else {
                    sendResponse(exchange, 400, "Invalid command");
                }
            }
            else {
                sendResponse(exchange, 400, "Missing command");
            }

        }

        /**
         * Method to handle the create user command
         * 
         * @param exchange the HttpExchange object for the request and response
         * @param userData the hashmap containing user data per attribute for object creation
         * @throws IOException if an I/O error occurs
         */
        private void handleCreate(HttpExchange exchange, Map<String, String> userData) throws IOException {
            
            // if all the required fields are present
            if (userData.containsKey("id") && userData.containsKey("username") && 
            userData.containsKey("email") && userData.containsKey("password")) {

                // get the ID
                int id = Integer.parseInt(userData.get("id"));

                if (userDatabase.containsKey(id)) {
                    // there is a conflict between an existing userId and the new one
                    sendResponse(exchange, 409, "UserID already exists in system");
                    return;
                }

                // hash the password for the user object
                String passwordHash = hashPassword(userData.get("password"));

                // create the new User object to be kept in the system
                User user = new User(id, userData.get("username"), userData.get("email"), passwordHash);
                userDatabase.put(id, user);

                // might need to send back some response - the json verson of the created user?
                sendResponse(exchange, 201, "User created successfully.");
            }
            else {
                sendResponse(exchange, 400, "Field(s) missing");
                return;
            }

        }

        /**
         * Method to handle the delete user command
         * 
         * @param exchange the HttpExchange object for the request and response
         * @param userData the hashmap containing user data per attribute for object deletion
         * @throws IOException if an I/O error occurs
         */
        private void handleDelete(HttpExchange exchange, Map<String, String> userData) throws IOException {
            // verify all the required fields are present
            if (userData.containsKey("id") && userData.containsKey("username") && 
                userData.containsKey("email") && userData.containsKey("password")) {
                
                // get the ID
                int id = Integer.parseInt(userData.get("id"));
                
                // verify the userID exists
                if (userDatabase.containsKey(id)) {
                    User user = userDatabase.get(id);
                    String password = hashPassword(userData.get("password"));

                    // verify that the user details match
                    if (user.username.equals(userData.get("username")) &&
                        user.passwordHash.equals(password) &&
                        user.email.equals(userData.get("email"))) {
                        
                        // proceed to delete the user
                        userDatabase.remove(id);
                        sendResponse(exchange, 200, "successfully deleted the user.");
                        return;
                    }

                    // if the details don't match
                    else {
                        sendResponse(exchange, 403, "User details do not match.");
                        return;
                    }
                }

                // if the userID does not exist
                else {
                    sendResponse(exchange, 404, "UserID not found in system");
                    return;

                }

            }

            // if the required field(s) is/are missing 
            else {
                sendResponse(exchange, 400, "Field(s) missing");
                return;
            }
        }

        /**
         * Method to handle the update user command
         * 
         * @param exchange
         * @param userData
         * @throws IOException
         */
        private void handleUpdate(HttpExchange exchange, Map<String, String> userData) throws IOException {
            // verify the userID is present
            if (userData.containsKey("id")) {
                int id = Integer.parseInt(userData.get("id"));
                
                // verify the system contains the userID
                if (userDatabase.containsKey(id)) {
                    User user = userDatabase.get(id);

                    if (userData.containsKey("username")) {
                        user.username = userData.get("username");
                    }
                    if (userData.containsKey("email")) {
                        user.email = userData.get("email");
                    }
                    if (userData.containsKey("password")) {
                        user.passwordHash = hashPassword(userData.get("password"));
                    }
                    sendResponse(exchange, 200, "User updated successfully.");
                }
                // if the userID does not exist in the system
                else {
                    sendResponse(exchange, id, response);
                    return;
                }
            }

            // if the userID is missing
            else {
                sendResponse(exchange, 400, "UserID is required for update.");
                return;
            }
        }
            
    }

    // ========================================================================

    /** 
     * Class User to represent user(s) in memory. 
     * 
     * @author misraagn
    */
    static class User {
        int id;
        String username;
        String email;
        String passwordHash;

        /**
         * 
         * @param id the userID
         * @param username the username
         * @param email the email of the user
         * @param passwordHash the hashed password of the user
         */
        public User(int id, String username, String email, String passwordHash) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.passwordHash = passwordHash;
        }
    }

    /**
     * Function to return a hashed version of the password
     * 
     * @param password the password to be hashed
     * @return String the final hashed password
     */
    private static String hashPassword(String password) {
`        
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                
            }
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 
     * private method to get the port number from the config file (.json format). 
     * 
     * @param filename the path to the config file
     * @return int the port number in the range 0 - 65 535
     */
    private static int getConfigPort(String filename) throws IOException{
        // read the entire file into a string
        String text = new String(Files.readAllBytes(Paths.get(filename)));

        // compile a regex pattern to extract the port number for UserService
        Pattern pattern = java.util.regex.Pattern.compile(
            "\"UserService\"\\s*:\\s*\\{[^}]*\"port\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);
        
        // return the port number
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new IOException("No port found for UserService in Config file: " + filename);
        }
    }

    /**
     * Reads entire request from the input stream of the exchange
     * This function is from the class examples.
     * 
     * @param exchange the HttpExchange object to read from
     * @return String represenatation of the request body
     * @throws IOException if an I/O error occurs
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

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
}
