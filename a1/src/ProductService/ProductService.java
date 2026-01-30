package ProductService;

import Helpers.Helpers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ProductService {
    // memory database to store products
    private static final Map<Integer, String> productDataBase = new HashMap<>();
    
    /**
     * The main function for the ProductService class
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

                // use the config file to get the products server's ip and port
        String config = new String(Files.readAllBytes(Paths.get(args[0])));
        String ip = Helpers.getIP(config, "ProductService");
        int port = Helpers.getPort(config, "ProductService");

        // check the validity of the results
        if (port == -1 || ip == null) {
            System.err.println("Could not parse the config file");
            System.exit(1);
        }

        // create and start the http server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        server.createContext("/product", new ProductHandler());

        // no concurrency needed for A1 as seen on piazza (CHANGE IN A2)
        server.setExecutor(null);
        server.start();
        System.out.println("ProductService started: " + ip + ":" + port);
    }

    /**
     * The http handler class for product Service
     * 
     * @author Agnibha Misra 
     */
    public static class ProductHandler implements HttpHandler {
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
         * The GET method handler for the ProductServices class
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
                if (productDataBase.containsKey(id)) {
                    // send a message back, the products's information
                    byte[] bytes = productDataBase.get(id).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }
                else {
                    // send a message back, the product does not exists
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
         * The POST method handler for the productServices class
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
            if (command == null || command.isEmpty() || id == null || id < 0) {
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
                // check if product is in the data base
                if (!productDataBase.containsKey(id)) {
                    // send a message back, the target product to delete doesn't exist it the database
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // get the product data from the database
                String productObject = productDataBase.get(id);
                String DBName = Helpers.parseString(productObject, "name");
                Float DBPrice = Helpers.parseFloat(productObject, "price");
                Integer DBQuantity = Helpers.parseInteger(productObject, "quantity");

                // get the product data from the request
                String requestName = Helpers.parseString(body, "name");
                Float requestPrice = Helpers.parseFloat(body, "price");
                Integer requestQuantity = Helpers.parseInteger(body, "quantity");

                // check if there was any error parsing the values
                if ((DBName == null || requestName == null) || (DBPrice == null || requestPrice == null) || (DBQuantity == null || requestQuantity == null)) {
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }

                // check for any mismatch
                if (!DBName.equals(requestName) || DBQuantity != requestQuantity || (Math.abs(requestPrice - DBPrice) < 0.0001)) {
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }

                // can delete safely
                else {
                    // send the success message
                    productDataBase.remove(id);
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                }

            }

            // Create case:
            else if (command.equalsIgnoreCase("create")) {
                // check whether the product already is in the database
                if (productDataBase.containsKey(id)) {
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(409, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // Parse the data
                String name = Helpers.parseString(body, "name");
                String description = Helpers.parseString(body, "description");
                Integer quantity = Helpers.parseInteger(body, "quantity");
                Float price = Helpers.parseFloat(body, "price");

                // check for bad inputs
                if (name == null || name.isEmpty() || description == null || quantity == null || quantity < 0 || price == null || price < 0) {
                    // send message back, empty case (failed parsing or bad request input)
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                String productObject = String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, name, description, price, quantity);
                // put the product JSON obect into the data base and send a success message
                productDataBase.put(id, productObject);
                byte[] bytes = productObject.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            
            
            }

            // Update case: 
            else if (command.equalsIgnoreCase("update")) {
                // make sure the product exists in the database
                if (!productDataBase.containsKey(id)) {
                    // product does not exist in the database
                    byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                    return;
                }

                // pull the object from the databse
                String productObject = productDataBase.get(id);

                // get the non-updated metadata of the object
                String name = Helpers.parseString(productObject, "name");
                String description = Helpers.parseString(productObject, "description");
                Float price = Helpers.parseFloat(productObject, "price");
                Integer quantity = Helpers.parseInteger(productObject, "quantity");

                // get the updated metadata of the object
                String updatedName = Helpers.parseString(body, "name");
                String updatedDescription = Helpers.parseString(body, "description");
                Float updatedPrice = Helpers.parseFloat(body, "price");
                Integer updatedQuantity = Helpers.parseInteger(body, "quantity");

                // check if the parameters need updating, if so then update
                if (updatedName != null) {
                    if (updatedName.isEmpty()) {
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                        return;
                    }
                    name = updatedName;
                }
                if  (updatedDescription != null) {
                    if (updatedDescription.isEmpty()) {
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                        return;
                    }
                    description = updatedDescription;
                }
                if (updatedPrice != null) {
                    if (updatedPrice < 0) {
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                        return;
                    }
                    price = updatedPrice;
                }
                if (updatedQuantity != null) {
                    if (updatedQuantity < 0) {
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                        return;
                    }
                    quantity = updatedQuantity;
                }

                if (updatedPrice != null) {
                    if (updatedPrice < 0) {
                        byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                        return;
                    }
                    price = updatedPrice;
                }

                // create the new product JSON object and place it into the database
                String updatedObject = String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, name, description, price, quantity);
                productDataBase.put(id, updatedObject);
                
                // send the success response back
                byte[] bytes = updatedObject.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;

            }
            // unknown command case
            else {
                byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                return;
            }
        }
    }
}
