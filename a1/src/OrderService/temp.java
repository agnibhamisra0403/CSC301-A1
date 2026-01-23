import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * OrderService - The public-facing API gateway for the microservices system.
 * 
 * <p>This service acts as the main entry point for all client requests and provides three key functions:
 * <ol>
 *   <li><b>Proxy/Forwarder</b>: Routes user and product requests to ISCS for load balancing and routing</li>
 *   <li><b>Order Processing</b>: Handles order placement logic including validation and inventory management</li>
 *   <li><b>Business Logic</b>: Implements the "place order" workflow by coordinating multiple service calls</li>
 * </ol>
 * 
 * <p><b>Architecture Flow:</b>
 * <pre>
 * Client → OrderService → ISCS → UserService/ProductService
 *                    ↓
 *              Order Logic
 *              (validates user, checks stock, updates inventory)
 * </pre>
 * 
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>POST /user - Proxied to UserService via ISCS</li>
 *   <li>GET /user/{id} - Proxied to UserService via ISCS</li>
 *   <li>POST /product - Proxied to ProductService via ISCS</li>
 *   <li>GET /product/{id} - Proxied to ProductService via ISCS</li>
 *   <li>POST /order - Handled locally (place order logic)</li>
 * </ul>
 * 
 * <p><b>Configuration:</b>
 * Requires config.json with OrderService and InterServiceCommunication sections.
 * 
 * <p><b>Usage:</b>
 * java OrderService config.json
 * 
 * @author Assignment Team
 * @version 1.0
 */
public class temp {

    /**
     * Base URL for the Inter-Service Communication Service (ISCS).
     * All requests to User and Product services are routed through ISCS.
     * Format: "http://IP:PORT" (e.g., "http://127.0.0.1:14004")
     */
    private static String ISCS_URL;

    /**
     * Main entry point for the OrderService.
     * 
     * <p>Startup sequence:
     * <ol>
     *   <li>Validates command-line arguments (requires config.json path)</li>
     *   <li>Loads and parses configuration file</li>
     *   <li>Extracts OrderService port and ISCS connection details</li>
     *   <li>Creates HTTP server and registers route handlers</li>
     *   <li>Starts listening for incoming requests</li>
     * </ol>
     * 
     * <p><b>Route Registration:</b>
     * <ul>
     *   <li>/user → ForwardHandler (proxy to ISCS)</li>
     *   <li>/product → ForwardHandler (proxy to ISCS)</li>
     *   <li>/order → OrderHandler (local business logic)</li>
     * </ul>
     * 
     * @param args Command-line arguments. Expected: args[0] = path to config.json
     * @throws IOException If config file cannot be read or server cannot start
     */
    public static void main(String[] args) throws IOException {
        // Validate command-line arguments
        if (args.length < 1) {
            System.err.println("Usage: java OrderService <config.json>");
            System.exit(1);
        }

        // Step 1: Load configuration file
        String configContent = new String(Files.readAllBytes(Paths.get(args[0])));
        
        // Step 2: Parse configuration values
        // Extract this service's port number
        int myPort = JsonUtils.getServicePort(configContent, "OrderService");
        
        // Extract ISCS connection details (where to forward requests)
        String iscsIp = JsonUtils.getServiceIp(configContent, "InterServiceCommunication");
        int iscsPort = JsonUtils.getServicePort(configContent, "InterServiceCommunication");

        // Validate that all required configuration was found
        if (myPort == -1 || iscsIp == null || iscsPort == -1) {
            System.err.println("Error parsing config.json");
            System.exit(1);
        }

        // Step 3: Construct the base URL for ISCS
        // All user/product requests will be forwarded to this URL
        ISCS_URL = "http://" + iscsIp + ":" + iscsPort;

        // Step 4: Create and configure HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(myPort), 0);

        // Step 5: Register route handlers
        // These contexts determine which handler processes each incoming request
        
        // Forward all /user requests to ISCS (e.g., /user/123, /user)
        server.createContext("/user", new ForwardHandler());
        
        // Forward all /product requests to ISCS (e.g., /product/456, /product)
        server.createContext("/product", new ForwardHandler());
        
        // Handle /order requests locally with business logic
        server.createContext("/order", new OrderHandler());

        // Use default executor (creates threads as needed)
        server.setExecutor(null);
        
        // Step 6: Start the server
        server.start();
        System.out.println("OrderService running on port " + myPort + " | Forwarding to ISCS at " + ISCS_URL);
    }

    // ============================================================
    // Handler 1: The Forwarder (Proxy)
    // Forwards /user and /product requests directly to ISCS
    // ============================================================
    
    /**
     * ForwardHandler - Proxy handler that forwards requests to ISCS.
     * 
     * <p>This handler implements a simple reverse proxy pattern. It:
     * <ol>
     *   <li>Receives a request from the client (WorkloadParser or other)</li>
     *   <li>Forwards the exact same request to ISCS</li>
     *   <li>Receives the response from ISCS</li>
     *   <li>Sends that response back to the original client</li>
     * </ol>
     * 
     * <p><b>Why proxy through OrderService?</b>
     * <ul>
     *   <li>Single entry point for all client requests</li>
     *   <li>Clients don't need to know about ISCS or service locations</li>
     *   <li>Easier to add authentication, logging, or rate limiting later</li>
     *   <li>Prepares architecture for A2 scalability requirements</li>
     * </ul>
     * 
     * <p><b>Request Flow Example:</b>
     * <pre>
     * Client sends:    POST /user → OrderService:14003
     * OrderService:    POST /user → ISCS:14004
     * ISCS:            POST /user → UserService:14001
     * UserService:     Returns 200 OK → ISCS
     * ISCS:            Returns 200 OK → OrderService
     * OrderService:    Returns 200 OK → Client
     * </pre>
     */
    static class ForwardHandler implements HttpHandler {
        /**
         * Handles an incoming HTTP request by forwarding it to ISCS.
         * 
         * <p>Process:
         * <ol>
         *   <li>Extract method (GET/POST), path, and body from incoming request</li>
         *   <li>Forward to ISCS with same method, path, and body</li>
         *   <li>Receive response from ISCS</li>
         *   <li>Send ISCS response back to client with same status code and body</li>
         * </ol>
         * 
         * <p>Error Handling:
         * If anything goes wrong (network error, ISCS down, etc.), returns 500 Internal Server Error.
         * 
         * @param t HttpExchange object representing the request/response
         * @throws IOException If there's an error reading request or writing response
         */
        @Override
        public void handle(HttpExchange t) throws IOException {
            try {
                // Step 1: Capture original request details from the client
                String method = t.getRequestMethod();  // "GET" or "POST"
                String path = t.getRequestURI().toString();  // e.g., "/user/123" or "/product"
                
                // Read the request body (empty for GET requests)
                String body = new BufferedReader(new InputStreamReader(t.getRequestBody()))
                        .lines().collect(Collectors.joining("\n"));

                // Step 2: Forward the request to ISCS
                // Construct full URL: ISCS_URL + path
                // Example: "http://127.0.0.1:14004" + "/user/123" = "http://127.0.0.1:14004/user/123"
                Response response = sendRequest(ISCS_URL + path, method, body);

                // Step 3: Send ISCS's response back to the original client
                // Use the same status code and body that ISCS returned
                byte[] responseBytes = response.body.getBytes();
                t.sendResponseHeaders(response.code, responseBytes.length);
                OutputStream os = t.getResponseBody();
                os.write(responseBytes);
                os.close();

            } catch (Exception e) {
                // If anything goes wrong (ISCS down, network error, etc.)
                // Return 500 Internal Server Error
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
                t.getResponseBody().close();
            }
        }
    }

    // ============================================================
    // Handler 2: The Order Logic
    // Handles POST /order to place orders
    // ============================================================
    
    /**
     * OrderHandler - Implements the "place order" business logic.
     * 
     * <p>This handler processes order placement requests by coordinating multiple service calls.
     * Unlike ForwardHandler, this implements actual business logic rather than just proxying.
     * 
     * <p><b>Place Order Workflow:</b>
     * <ol>
     *   <li>Parse and validate request (command, user_id, product_id, quantity)</li>
     *   <li>Verify user exists (GET /user/{user_id} via ISCS)</li>
     *   <li>Verify product exists (GET /product/{product_id} via ISCS)</li>
     *   <li>Check if sufficient stock available</li>
     *   <li>Update product inventory (POST /product with reduced quantity via ISCS)</li>
     *   <li>Generate order ID and save order record</li>
     *   <li>Return success response with order details</li>
     * </ol>
     * 
     * <p><b>Response Status Messages:</b>
     * <ul>
     *   <li>"Success" - Order placed successfully</li>
     *   <li>"Invalid Request" - Missing fields, user not found, or product not found</li>
     *   <li>"Exceeded quantity limit" - Not enough stock available</li>
     * </ul>
     * 
     * <p><b>API Specification:</b>
     * <pre>
     * Request:
     * POST /order
     * {
     *   "command": "place order",
     *   "user_id": 1,
     *   "product_id": 3,
     *   "quantity": 2
     * }
     * 
     * Success Response (200):
     * {
     *   "id": 12345,
     *   "user_id": 1,
     *   "product_id": 3,
     *   "quantity": 2,
     *   "status": "Success"
     * }
     * 
     * Error Response (400):
     * {
     *   "status": "Exceeded quantity limit"
     * }
     * </pre>
     */
    static class OrderHandler implements HttpHandler {
        /**
         * Handles POST /order requests to place orders.
         * 
         * <p>This method orchestrates the entire order placement workflow,
         * making multiple calls to other services via ISCS and implementing
         * business rules like stock validation.
         * 
         * @param t HttpExchange object representing the request/response
         * @throws IOException If there's an error reading request or writing response
         */
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Only accept POST requests for placing orders
            if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
                t.sendResponseHeaders(405, 0); // 405 Method Not Allowed
                t.getResponseBody().close();
                return;
            }

            try {
                // Step 1: Parse the request body
                String requestBody = new BufferedReader(new InputStreamReader(t.getRequestBody()))
                        .lines().collect(Collectors.joining("\n"));

                // Extract the command field
                String command = JsonUtils.parseString(requestBody, "command");

                // Validate that this is a "place order" command
                if (!"place order".equalsIgnoreCase(command)) {
                    sendJson(t, 400, "{\"status\": \"Invalid Request\"}");
                    return;
                }

                // Extract required fields from the request
                Integer userId = JsonUtils.parseInt(requestBody, "user_id");
                Integer productId = JsonUtils.parseInt(requestBody, "product_id");
                Integer quantityNeeded = JsonUtils.parseInt(requestBody, "quantity");

                // Validate that all required fields are present
                if (userId == null || productId == null || quantityNeeded == null) {
                    sendJson(t, 400, "{\"status\": \"Invalid Request\"}");
                    return;
                }

                // Step 2: Verify the user exists
                // Make a GET request to /user/{user_id} via ISCS
                Response userRes = sendRequest(ISCS_URL + "/user/" + userId, "GET", null);
                
                if (userRes.code == 404) {
                    // User doesn't exist - return error
                    sendJson(t, 400, "{\"status\": \"Invalid Request\"}");
                    return;
                }

                // Step 3: Verify the product exists and get its details
                // Make a GET request to /product/{product_id} via ISCS
                Response productRes = sendRequest(ISCS_URL + "/product/" + productId, "GET", null);
                
                if (productRes.code == 404) {
                    // Product doesn't exist - return error
                    sendJson(t, 400, "{\"status\": \"Invalid Request\"}");
                    return;
                }

                // Step 4: Check if there's enough stock available
                // Parse the quantity field from the product response
                Integer currentStock = JsonUtils.parseInt(productRes.body, "quantity");
                
                if (currentStock == null || currentStock < quantityNeeded) {
                    // Not enough stock - return error
                    sendJson(t, 400, "{\"status\": \"Exceeded quantity limit\"}");
                    return;
                }

                // Step 5: Update the product inventory
                // Calculate new stock level after this order
                int newStock = currentStock - quantityNeeded;
                
                // Build update request payload
                // Note: According to API spec, only updating quantity is fine (partial update allowed)
                String updatePayload = String.format(
                    "{\"command\": \"update\", \"id\": %d, \"quantity\": %d}", 
                    productId, newStock
                );
                
                // Send POST request to update the product
                Response updateRes = sendRequest(ISCS_URL + "/product", "POST", updatePayload);

                // Step 6: Check if inventory update succeeded
                if (updateRes.code == 200) {
                    // Success! The order can be completed
                    
                    // Generate a unique order ID
                    // Using timestamp modulo for simplicity (could use UUID or sequential ID)
                    int orderId = (int)(System.currentTimeMillis() % 100000); 

                    // Step 7: Save order to local storage for persistence
                    // This ensures orders survive service restarts (requirement from assignment)
                    saveOrderLocally(orderId, userId, productId, quantityNeeded);

                    // Step 8: Construct success response JSON
                    String responseJson = String.format(
                        "{\"id\": %d, \"product_id\": %d, \"user_id\": %d, \"quantity\": %d, \"status\": \"Success\"}",
                        orderId, productId, userId, quantityNeeded
                    );

                    // Send 200 OK with order details
                    sendJson(t, 200, responseJson);
                    
                } else {
                    // Inventory update failed - return error
                    // This could happen if ProductService is down or there's a database error
                    sendJson(t, 500, "{\"status\": \"Internal Error\"}");
                }

            } catch (Exception e) {
                // Catch any unexpected errors (parsing errors, network issues, etc.)
                e.printStackTrace();
                sendJson(t, 500, "{\"status\": \"Internal Server Error\"}");
            }
        }

        /**
         * Saves order information to local file for persistence.
         * 
         * <p>This simple persistence mechanism ensures that orders are not lost
         * if the OrderService restarts. Orders are appended to "orders.txt" in
         * CSV format: id,user_id,product_id,quantity
         * 
         * <p><b>Example orders.txt content:</b>
         * <pre>
         * 12345,1,3,2
         * 12346,2,5,1
         * 12347,1,3,5
         * </pre>
         * 
         * <p>Note: This is a simple implementation for A1. For A2, you'll likely
         * need a proper database to handle high volumes and concurrent access.
         * 
         * @param id Order ID
         * @param uid User ID who placed the order
         * @param pid Product ID that was ordered
         * @param qty Quantity ordered
         */
        private void saveOrderLocally(int id, int uid, int pid, int qty) {
            try (FileWriter fw = new FileWriter("orders.txt", true);  // true = append mode
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                
                // Write order as CSV: id,user_id,product_id,quantity
                out.println(id + "," + uid + "," + pid + "," + qty);
                
            } catch (IOException e) {
                // If file write fails, log but don't crash the service
                // The order was still placed successfully in terms of inventory
                System.err.println("Warning: Failed to save order to file: " + e.getMessage());
            }
        }

        /**
         * Helper method to send JSON response to client.
         * 
         * <p>Sets proper headers, status code, and writes JSON body.
         * 
         * @param t HttpExchange object for the request/response
         * @param code HTTP status code (200, 400, 500, etc.)
         * @param json JSON string to send as response body
         * @throws IOException If there's an error writing the response
         */
        private void sendJson(HttpExchange t, int code, String json) throws IOException {
            byte[] bytes = json.getBytes();
            
            // Send response headers with status code and content length
            t.sendResponseHeaders(code, bytes.length);
            
            // Write JSON body
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // ============================================================
    // HTTP Helper Methods
    // ============================================================
    
    /**
     * Simple container class for HTTP response data.
     * 
     * <p>Holds both the status code and response body together,
     * making it easier to pass response data between methods.
     * 
     * <p><b>Example usage:</b>
     * <pre>
     * Response res = sendRequest(url, "GET", null);
     * if (res.code == 200) {
     *     System.out.println("Success: " + res.body);
     * }
     * </pre>
     */
    static class Response {
        /** HTTP status code (200, 404, 500, etc.) */
        int code;
        
        /** Response body as a string (typically JSON) */
        String body;
        
        /**
         * Constructs a Response object.
         * 
         * @param code HTTP status code
         * @param body Response body content
         */
        public Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    /**
     * Sends an HTTP request to ISCS and returns the response.
     * 
     * <p>This is a general-purpose HTTP client method used by both handlers
     * to communicate with ISCS. It handles both GET and POST requests.
     * 
     * <p><b>Usage Examples:</b>
     * <pre>
     * // GET request (check if user exists)
     * Response res = sendRequest("http://127.0.0.1:14004/user/2", "GET", null);
     * 
     * // POST request (update product)
     * String json = "{\"command\":\"update\", \"id\":3, \"quantity\":10}";
     * Response res = sendRequest("http://127.0.0.1:14004/product", "POST", json);
     * </pre>
     * 
     * <p><b>Features:</b>
     * <ul>
     *   <li>Automatically sets Content-Type header for POST requests</li>
     *   <li>Handles both success and error response streams</li>
     *   <li>Returns both status code and body for easy processing</li>
     * </ul>
     * 
     * @param urlStr Complete URL to send request to (e.g., "http://127.0.0.1:14004/user/2")
     * @param method HTTP method ("GET" or "POST")
     * @param body Request body for POST requests (null for GET requests)
     * @return Response object containing status code and response body
     * @throws IOException If there's a network error or connection failure
     */
    public static Response sendRequest(String urlStr, String method, String body) throws IOException {
        // Step 1: Create URL and open connection
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        
        // Step 2: If this is a POST request with a body, send it
        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);  // Enable output stream for POST body
            
            // Set Content-Type header to tell server we're sending JSON
            conn.setRequestProperty("Content-Type", "application/json");
            
            // Write the body to the request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }
        }

        // Step 3: Get the HTTP response status code
        int code = conn.getResponseCode();
        
        // Step 4: Read the response body
        // For error responses (4xx, 5xx), we need to read from ErrorStream instead of InputStream
        InputStream stream = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        
        String responseBody = "";
        if (stream != null) {
            // Read all lines from the stream and join them
            responseBody = new BufferedReader(new InputStreamReader(stream))
                    .lines().collect(Collectors.joining("\n"));
        }
        
        // Step 5: Return both the status code and body
        return new Response(code, responseBody);
    }
}