package OrderService;

import Helpers.Helpers;
import OrderService.OrderService.ForwardHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Scanner;

/**
 * The API gateway for the system.
 * Requires config.json with OrderService and InterServiceCommunication sections.
 * 
 * @author Agnibha Misra
 */
public class OrderService {

    /**
     * URL for Inter-Service Communication.
     */
    private static String InterServiceCommunicationURL;

    /**
     * Main function for OrderService
     * @param args command line arguements
     * @throws IOException In the case a file (config.json) cannot be read or failure to start server. 
     */
    public static void main(String[] args) throws IOException {
        // make sure a config file was passed in
        if (args.length < 1) {
            System.err.println("Need config file as arguement");
            System.exit(1);
        }

        String config = new String(Files.readAllBytes(Paths.get(args[0])));
        
        // The OrderService port number
        int OrderServiceport = Helpers.getPort(config, "OrderService");
        
        // ISCS details
        int InterServiceCommunicationPort = Helpers.getPort(config, "InterServiceCommunication");
        String InterServiceCommunicationIP = Helpers.getIP(config, "InterServiceCommunication");
        
        if (InterServiceCommunicationIP == null || InterServiceCommunicationPort == -1 || OrderServiceport == -1) {
            System.err.println("Failed to parse config file");
            System.exit(1);
        }

        // create the URL for ISCS
        InterServiceCommunicationURL = "http://" + InterServiceCommunicationIP + ":" + InterServiceCommunicationPort; 

        // create the http server with the OrderService port
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(OrderServiceport), 0);

        // set the forward handlers to the appropriate unique ones
        httpServer.createContext("/order", new OrderHandler());
        httpServer.createContext("/user", new ForwardHandler()); 
        httpServer.createContext("/product", new ForwardHandler());

        // no concurrency needed for A1 as seen on piazza (CHANGE IN A2)
        httpServer.setExecutor(null);

        httpServer.start();
        System.out.println("OrderService server running: " + OrderServiceport);
        System.err.println("Forwarding to InterServiceCommunication: " + InterServiceCommunicationURL);
    }

    /**
     * Handler that forwards requests to ISCS
     */
    public static class ForwardHandler implements HttpHandler {

        /**
         * Handles incoming HTTP requests by forwarding them. 
         * 
         * @param exchange the http exchange object used to represent request and response
         * @throws IOException if there is an error reading or writing.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // exchange object contains both path and method of the request
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                // read the body of the request using scanner(might need to change)
                Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
                String body;
                if (scanner.hasNext()) {
                    body = scanner.next();
                }
                else {
                    body = "";
                }

                // response is a object array as follows: [code, body]
                Object[] response = Helpers.requestSend(InterServiceCommunicationURL + path, method, body);

                // use index 1 for the body of the response
                byte[] responseBytes = ((String) response[1]).getBytes();
                exchange.sendResponseHeaders((int)response[0], responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            }
        }
    }
    /**
     * Processes the order placement via requests through multiple service calls
     * @author Agnibha Misra
     */
    public static class OrderHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // do not accept non post requests
            if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
                exchange.sendResponseHeaders(405, 0);
                exchange.getResponseBody().close();
                return; 
            }

            try {
                // read the request body
                Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
                String body;
                if (scanner.hasNext()) {
                    body = scanner.next();
                } else {
                    body = "";
                }
                scanner.close();
                
                // get the command from the body of the request
                String command = Helpers.parseString(body, "command");

                // make sure the command is place order
                if ("place order".equalsIgnoreCase(command)) {

                    // use the helpers to get the metadata needed
                    Integer productID = Helpers.parseInteger(body, "product_id");
                    Integer userID = Helpers.parseInteger(body, "user_id");
                    Integer quantity = Helpers.parseInteger(body, "quantity"); 

                    if (productID == null || userID == null || quantity == null) {
                        JsonSender(exchange, 400, "{\"status\": \"Invalid Request\"}");
                        return;
                    }

                    // use an empty request to /user to check if the user exists
                    Object[] response = Helpers.requestSend(InterServiceCommunicationURL + "/user/" + userID, "GET", null);
                    if ((int)response[0] == 404) {
                        // the user does not exist
                        JsonSender(exchange, 400, "{\"status\": \"Invalid Request\"}");
                        return;
                    }

                    // use an empty request to /product to check if product exists and get some metadata for further tests
                    Object[] response2 = Helpers.requestSend(InterServiceCommunicationURL + "/product/" + productID, "GET", null);
                    if ((int)response2[0] == 404) {
                        // the product does not exist
                        JsonSender(exchange, 400, "{\"status\": \"Invalid Request\"}");
                        return;
                    }

                    // check if there is enough of this product remaining
                    Integer remainingAmount = Helpers.parseInteger((String)response2[1], "quantity");
                    if (remainingAmount == null || remainingAmount < quantity) {
                        // not enough
                        JsonSender(exchange, 400, "{\"status\": \"Exceeded quantity limit\"}");
                        return;
                    }

                    // calculate the new remaining amount using a update request
                    remainingAmount -= quantity; 
                    
                    // the update request
                    String updateRequest = String.format("{\\\"command\\\": \\\"update\\\", \\\"id\\\": %d, \\\"quantity\\\": %d}", productID, (int)remainingAmount);
                    Object[] updateResponse = Helpers.requestSend(InterServiceCommunicationURL + "/product", "POST", updateRequest);

                    if ((int) updateResponse[0] == 200) {
                        // create a ID for the order
                        Random random = new Random();
                        int orderID = random.nextInt(100000); 

                        // save the Order locally for the sake of persistence
                        locallySaveOrder(orderID, userID, productID, quantity);

                        String jsonResponse = String.format("{\"id\": %d, \"product_id\": %d, \"user_id\": %d, \"quantity\": %d, \"status\": \"Success\"}", orderID, productID, userID, quantity);
                        
                        JsonSender(exchange, 200, jsonResponse);
                    
                    }
                    else {
                        JsonSender(exchange, 500, "{\"status\": \"Internal Error\"}");
                    }

                }
                else {
                    JsonSender(exchange, 400, "{\"status\": \"Invalid Request\"}");
                    return;
                }

            } catch (Exception e) {
                e.printStackTrace();
                JsonSender(exchange, 500, "{\"status\": \"Internal Server Error\"}");
            }
        }

        /**
         * sends JSON response to the client
         * 
         * @param exchange HttpExchange object for requests and responses
         * @param code status code
         * @param json json string to sent as response body
         * @throws IOException in the case there's an error in writing
         */
        public void JsonSender(HttpExchange exchange, int code, String json) throws IOException {
            byte[] bytes = json.getBytes();

            exchange.sendResponseHeaders(code, bytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        /**
         * Saves order information and metadata locally for the sake of persistence
         * (come back to this)
         * 
         * @param orderID the orderID of the order being saved
         * @param userID the userID of who placed the order
         * @param productID the productID that was placed
         * @param quantity quantity ordered
         */
        private void locallySaveOrder(int orderID, int userID, int productID, int quantity) {

        }
    }
    
}


