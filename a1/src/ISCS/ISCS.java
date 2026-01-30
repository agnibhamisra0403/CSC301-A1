package ISCS;

import Helpers.Helpers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
/**
 * The Inter-service Communication Service class. Acts as a central router 
 * and load balancer between the Order Service and the User/Product services.
 * @author Agnibha Misra
 */
public class ISCS {
    // URL of the userService
    private static String userURL;
    // URL of the productService
    private static String productURL;
    // URL of the orderService
    private static String orderURL;
    
    /**
     * Initializes the ISCS server and resolves internal service endpoints from config.json.
     * @param args command line arguments, expected to contain the path to config.json.
     * @throws IOException if error occurs during file reading or server binding.
     */
    public static void main (String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Need to provide a config file");
            System.exit(1);
        }

        

        // get the config file from the input parameters
        String config = new String(Files.readAllBytes(Paths.get(args[0])));

        // get the port of ISCS
        int port = Helpers.getPort(config, "InterServiceCommunication");

        int portOrder = Helpers.getPort(config, "OrderService");
        String IpOrder = Helpers.getIP(config, "OrderService");
        orderURL = "http://" + IpOrder + ":" + portOrder;

        // get the IP and  port of product and user
        int portProduct = Helpers.getPort(config, "ProductService");
        String IpProduct = Helpers.getIP(config, "ProductService");

        int portUser = Helpers.getPort(config, "UserService");
        String IpUser = Helpers.getIP(config, "UserService");

        // use the IP and the port to create the URLS
        productURL = "http://" + IpProduct + ":" + portProduct;
        userURL = "http://" + IpUser + ":" + portUser;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ISCSHandler());

        // no concurrency needed for A1 as seen on piazza (CHANGE IN A2)
        server.setExecutor(null);
        
        server.start();
        System.out.println("ISCS server started on: " + port);
        System.out.println("User route: " + userURL);
        System.out.println("Product route: " + productURL);
    }

    /**
     * The ISCSHandler examines incoming requests and sends them to the appropriate user/product service.
     * @author Agnibha Misra
     */
    public static class ISCSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // get the path in the form of /user... or /product...
                String path = exchange.getRequestURI().getPath();
                String url = "";

                // check whether the path starts with product or user and modify the URL appropriately
                if (path.startsWith("/product")) {
                    url = productURL + path;
                }
                else if (path.startsWith("/user")) {
                    url = userURL + path;
                }
                else if (path.startsWith("/order")) {
                    url = orderURL + path;
                }
                else {
                    // the path is invalid/doesn't exist
                    String response = "{\"status\": \"Invalid Path\"}";
                    exchange.sendResponseHeaders(404, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    return;
                }

                // read the request
                String method = exchange.getRequestMethod();
                Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
                String body;
                if (scanner.hasNext()) {
                    body = scanner.next();
                }
                else {
                    body = "";
                }

                // forward the request to product or user and get the status code and response
                Object[] response = Helpers.requestSend(url, method, body);
                int responseCode = (int)response[0];
                String responseString = (String)response[1];

                // forward the response back to the initial sender, orderService
                byte[] responseBytes = responseString.getBytes();
                exchange.sendResponseHeaders(responseCode, responseBytes.length);
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
}
