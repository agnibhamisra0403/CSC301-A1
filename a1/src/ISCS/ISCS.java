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
 * The Inter-service Communication Service class used as a router and load balancer
 * Sits between orderService and load/user services as seen in architecture.png
 * 
 * @author Agnibha Misra
 */
public class ISCS {
    // URL of the userService
    private static String userURL;
    // URL of the productService
    private static String productURL;
    
    /**
     * The main function for ISCS
     * @param args command line arguements
     * @throws IOException if error on read and write
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
     * 
     * @author Agnibha Misra
     */
    public static class ISCSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // get the path in the form of /user... or /product...
                String path = exchange.getRequestURI().toString();
                String url = "";

                // check whether the path starts with product or user and modify the URL appropriately
                if (path.startsWith("/product")) {
                    url = productURL + path;
                }
                else if (path.startsWith("/user")) {
                    url = userURL + path; 
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
