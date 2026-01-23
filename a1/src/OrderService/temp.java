import java.io.BufferedReader;
import java.io.FileReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorkloadParser - Automated testing tool for the microservices architecture.
 * 
 * This parser reads workload files containing USER, PRODUCT, and ORDER commands,
 * then translates them into HTTP requests sent to the Order Service.
 * The Order Service routes these requests through the ISCS (Inter-Service Communication Service)
 * to the appropriate microservice (User or Product service).
 * 
 * <p>Command Flow:
 * WorkloadParser → Order Service → ISCS → User/Product Service
 * 
 * <p>Usage:
 * java WorkloadParser workloadfile.txt
 * 
 * <p>Supported Commands:
 * <ul>
 *   <li>USER create/get/update/delete</li>
 *   <li>PRODUCT create/info/update/delete</li>
 *   <li>ORDER place</li>
 * </ul>
 * 
 * @author Assignment Team
 * @version 1.0
 */
public class temp {

    /**
     * The base URL for the Order Service (e.g., "http://127.0.0.1:14003").
     * This is constructed from config.json and used as the target for all HTTP requests.
     */
    private static String orderServiceUrl;

    /**
     * Main entry point for the WorkloadParser.
     * 
     * <p>Process:
     * 1. Validates command-line arguments
     * 2. Reads config.json to determine Order Service location
     * 3. Processes the workload file line by line
     * 
     * @param args Command-line arguments. Expected: args[0] = workload file path
     */
    public static void main(String[] args) {
        // Validate that a workload file was provided
        if (args.length < 1) {
            System.err.println("Usage: java WorkloadParser <workload_file>");
            System.exit(1);
        }

        String workloadFile = args[0];

        try {
            // Step 1: Read config.json to find the Order Service IP and port
            // The config file should be in the current directory or parent directory
            String configContent = new String(Files.readAllBytes(Paths.get("config.json")));
            
            // Parse the OrderService section to extract IP and port
            String ip = parseConfig(configContent, "OrderService", "ip");
            String port = parseConfig(configContent, "OrderService", "port");
            
            // Fallback to default values if config parsing fails
            if (ip == null || port == null) {
                ip = "127.0.0.1";
                port = "14003";
                System.out.println("Warning: Could not parse config. Using default " + ip + ":" + port);
            }
            
            // Construct the base URL for all HTTP requests
            orderServiceUrl = "http://" + ip + ":" + port;
            System.out.println("Targeting OrderService at: " + orderServiceUrl);

            // Step 2: Process each line in the workload file
            processWorkload(workloadFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes the workload file line by line, parsing commands and sending HTTP requests.
     * 
     * <p>Workload File Format:
     * Each line contains a command in the format:
     * SERVICE COMMAND [PARAMETERS...]
     * 
     * <p>Example lines:
     * <pre>
     * USER create 2 username2NKlLXs elOf@Y4vxdHuRs2620f.com bY3NAKPS
     * PRODUCT info 4
     * ORDER place 3 1 1
     * # This is a comment
     * </pre>
     * 
     * <p>The method:
     * 1. Reads each line from the file
     * 2. Skips empty lines, comments (#), and metadata ([])
     * 3. Parses the service type (USER/PRODUCT/ORDER) and command
     * 4. Constructs appropriate JSON payload
     * 5. Sends HTTP request to Order Service
     * 6. Measures total execution time
     * 
     * @param filename Path to the workload file
     */
    private static void processWorkload(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            long startTime = System.currentTimeMillis();

            // Read file line by line
            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines, comments, and metadata
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;

                // Split the line into tokens (space-separated)
                // Example: "USER create 2 username email password"
                // parts[0] = "USER", parts[1] = "create", parts[2] = "2", etc.
                String[] parts = line.split("\\s+");
                String service = parts[0].toUpperCase(); // USER, PRODUCT, or ORDER
                String command = parts[1].toLowerCase(); // create, update, delete, get, info, place

                // Variables for building the HTTP request
                String endpoint = "";       // URL path (e.g., "/user" or "/user/2")
                String method = "POST";     // HTTP method (GET or POST)
                String jsonPayload = "";    // JSON body for POST requests

                // --- USER COMMANDS ---
                // Handle all USER service operations
                if (service.equals("USER")) {
                    endpoint = "/user";
                    
                    if (command.equals("create")) {
                        // USER create <id> <username> <email> <password>
                        // Example: USER create 2 username2NKlLXs elOf@Y4vxdHuRs2620f.com bY3NAKPS
                        // Creates a new user with the specified credentials
                        jsonPayload = String.format(
                            "{\"command\":\"create\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}",
                            parts[2], parts[3], parts[4], parts[5]
                        );
                        
                    } else if (command.equals("get")) {
                        // USER get <id>
                        // Example: USER get 2
                        // Retrieves user information for the specified ID
                        // This uses a GET request with the ID in the URL path
                        method = "GET";
                        endpoint = "/user/" + parts[2];
                        
                    } else if (command.equals("update")) {
                        // USER update <id> username:newusername email:newemail password:newpass
                        // Example: USER update 2 username:un-4Vofkp8EyolNJYJ email:glud@Ucdl4s6HBjq.com
                        // Updates user fields. Only fields provided are updated (partial updates allowed)
                        jsonPayload = buildUpdateJson(parts, "update");
                        
                    } else if (command.equals("delete")) {
                        // USER delete <id> <username> <email> <password>
                        // Example: USER delete 2 4Vofkp8EyolNJYJ glud@Ucdl4s6HBjq.com BkPpkkW1
                        // Deletes a user ONLY if all credentials match (authentication required)
                        jsonPayload = String.format(
                            "{\"command\":\"delete\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}",
                            parts[2], parts[3], parts[4], parts[5]
                        );
                    }
                } 
                // --- PRODUCT COMMANDS ---
                // Handle all PRODUCT service operations
                else if (service.equals("PRODUCT")) {
                    endpoint = "/product";
                    
                    if (command.equals("create")) {
                        // PRODUCT create <id> <name> <price> <quantity>
                        // Example: PRODUCT create 2 productname-2398 3.99 9
                        // Creates a new product with the specified attributes
                        // Note: Using name as description for simplicity (as per assignment hint)
                        jsonPayload = String.format(
                            "{\"command\":\"create\", \"id\":%s, \"name\":\"%s\", \"description\":\"%s\", \"price\":%s, \"quantity\":%s}",
                            parts[2], parts[3], parts[3], parts[4], parts[5]
                        );
                        
                    } else if (command.equals("info")) {
                        // PRODUCT info <id>
                        // Example: PRODUCT info 4
                        // Retrieves product information for the specified ID
                        method = "GET";
                        endpoint = "/product/" + parts[2];
                        
                    } else if (command.equals("update")) {
                        // PRODUCT update <id> name:newname price:4.99 quantity:20
                        // Example: PRODUCT update 4 name:granola price:4.99 quantity:20
                        // Updates product fields. Only fields provided are updated
                        jsonPayload = buildUpdateJson(parts, "update");
                        
                    } else if (command.equals("delete")) {
                        // PRODUCT delete <id> <name> <price> <quantity>
                        // Example: PRODUCT DELETE 4 granola 4.99 20
                        // Deletes a product if all fields match (authentication required)
                        jsonPayload = String.format(
                            "{\"command\":\"delete\", \"id\":%s, \"name\":\"%s\", \"price\":%s, \"quantity\":%s}",
                            parts[2], parts[3], parts[4], parts[5]
                        );
                    }
                } 
                // --- ORDER COMMANDS ---
                // Handle ORDER service operations
                else if (service.equals("ORDER")) {
                    endpoint = "/order";
                    
                    if (command.equals("place")) {
                        // ORDER place <product_id> <user_id> <quantity>
                        // Example: ORDER place 3 1 1
                        // Places an order for a user to purchase a product
                        // This will check if user exists, product exists, and sufficient quantity available
                        jsonPayload = String.format(
                            "{\"command\":\"place order\", \"product_id\":%s, \"user_id\":%s, \"quantity\":%s}",
                            parts[2], parts[3], parts[4]
                        );
                    }
                }

                // Send the HTTP request if a valid endpoint was determined
                if (!endpoint.isEmpty()) {
                    sendRequest(method, orderServiceUrl + endpoint, jsonPayload);
                }
            }
            
            // Calculate and display total execution time
            long endTime = System.currentTimeMillis();
            System.out.println("Workload finished in " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructs a JSON payload for UPDATE commands from "key:value" formatted parameters.
     * 
     * <p>This helper method handles the parsing of update commands where fields are specified
     * as "fieldname:value" pairs. It intelligently handles numeric vs. string values.
     * 
     * <p>Input Format Examples:
     * <pre>
     * USER update 2 username:newuser email:new@email.com password:newpass
     * PRODUCT update 4 name:granola price:4.99 quantity:20
     * </pre>
     * 
     * <p>Output JSON Example:
     * <pre>
     * {"command":"update", "id":2, "username":"newuser", "email":"new@email.com", "password":"newpass"}
     * </pre>
     * 
     * <p>Algorithm:
     * 1. Start with command and id
     * 2. Iterate through remaining parts (starting at index 3)
     * 3. Split each part on ':' to get key-value pairs
     * 4. Determine if value should be quoted (string) or not (number)
     * 5. Build JSON string with proper formatting
     * 
     * @param parts The tokenized command line (space-separated)
     *              Format: [SERVICE, COMMAND, ID, field:value, field:value, ...]
     * @param commandName The command type (typically "update")
     * @return A JSON string representing the update request
     */
    private static String buildUpdateJson(String[] parts, String commandName) {
        StringBuilder json = new StringBuilder();
        
        // Start building the JSON with command and id
        // parts[2] is the ID (e.g., in "USER update 2 username:...", parts[2] = "2")
        json.append("{\"command\":\"").append(commandName).append("\", \"id\":").append(parts[2]);
        
        // Process each "key:value" pair starting from index 3
        // Example: "username:newuser" becomes {"username":"newuser"}
        for (int i = 3; i < parts.length; i++) {
            String[] kv = parts[i].split(":");
            
            // Only process if we have a valid key:value pair
            if (kv.length == 2) {
                json.append(", \"").append(kv[0]).append("\":");
                
                // Heuristic to determine if value should be numeric or string
                // price and quantity are numeric fields, others are strings
                if (kv[0].equals("price") || kv[0].equals("quantity")) {
                    // Numeric value - no quotes
                    json.append(kv[1]);
                } else {
                    // String value - add quotes
                    json.append("\"").append(kv[1]).append("\"");
                }
            }
        }
        
        json.append("}");
        return json.toString();
    }

    /**
     * Sends an HTTP request to the Order Service and prints the response status code.
     * 
     * <p>This method handles both GET and POST requests:
     * <ul>
     *   <li>GET: Retrieves data (used for user/product lookups)</li>
     *   <li>POST: Sends data (used for create/update/delete/order operations)</li>
     * </ul>
     * 
     * <p>Connection Details:
     * - Content-Type: application/json (all requests use JSON format)
     * - Timeout: Uses default Java timeout settings
     * - Error Handling: Catches connection failures gracefully
     * 
     * <p>Response Handling:
     * The method only prints the HTTP status code. The actual validation
     * of responses (checking for correct data, status codes, etc.) is done
     * by the TAs' testing framework.
     * 
     * <p>Status Code Meanings:
     * - 200: Success
     * - 400: Bad Request (invalid/missing fields)
     * - 401: Unauthorized (authentication failed)
     * - 404: Not Found (entity doesn't exist)
     * - 409: Conflict (duplicate ID)
     * - 500: Internal Server Error
     * 
     * @param method HTTP method ("GET" or "POST")
     * @param urlStr Complete URL including endpoint (e.g., "http://127.0.0.1:14003/user/2")
     * @param jsonPayload JSON body for POST requests (empty string for GET requests)
     */
    private static void sendRequest(String method, String urlStr, String jsonPayload) {
        try {
            // Create URL object and open connection
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            
            // Set HTTP method (GET or POST)
            conn.setRequestMethod(method);
            
            // Set content type to JSON for all requests
            conn.setRequestProperty("Content-Type", "application/json");
            
            // For POST requests, write the JSON payload to the request body
            if (method.equals("POST") && !jsonPayload.isEmpty()) {
                conn.setDoOutput(true); // Enable output stream for POST body
                
                // Write JSON data to the output stream
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
            }

            // Get the HTTP response status code
            // This triggers the actual request to be sent
            int code = conn.getResponseCode();
            
            // Print the result for logging/debugging
            // Format: "POST http://127.0.0.1:14003/user [200]"
            System.out.println(method + " " + urlStr + " [" + code + "]");
            
        } catch (Exception e) {
            // If connection fails (service down, network error, etc.), print error
            System.out.println("Failed to connect to " + urlStr);
        }
    }

    /**
     * Parses config.json to extract IP address or port for a specific service.
     * 
     * <p>This is a lightweight JSON parser that uses regex to extract values
     * without requiring external JSON libraries (which aren't allowed per assignment requirements).
     * 
     * <p>Config.json Format:
     * <pre>
     * {
     *   "OrderService": {
     *     "port": 14003,
     *     "ip": "127.0.0.1"
     *   },
     *   "UserService": {
     *     "port": 14001,
     *     "ip": "127.0.0.1"
     *   }
     * }
     * </pre>
     * 
     * <p>Regex Pattern Explanation:
     * The regex searches for: "ServiceName": { ... "key": "value" ... }
     * It handles both quoted strings ("127.0.0.1") and unquoted numbers (14003)
     * 
     * <p>Example Usage:
     * <pre>
     * parseConfig(jsonString, "OrderService", "ip")     // Returns "127.0.0.1"
     * parseConfig(jsonString, "OrderService", "port")   // Returns "14003"
     * </pre>
     * 
     * @param json The entire config.json file content as a string
     * @param service The service name to look for (e.g., "OrderService", "UserService")
     * @param key The specific key to extract (e.g., "ip", "port")
     * @return The value as a string, or null if not found
     */
    private static String parseConfig(String json, String service, String key) {
        // Build regex pattern to find: "ServiceName": { ... "key": "value" ... }
        // Pattern matches the service block and extracts the key's value
        // The \"? makes quotes optional (handles both strings and numbers)
        Pattern pattern = Pattern.compile(
            "\"" + service + "\"\\s*:\\s*\\{[^}]*\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?"
        );
        
        Matcher matcher = pattern.matcher(json);
        
        // If pattern found, return the captured value (group 1)
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Return null if service or key not found in config
        return null;
    }
}




