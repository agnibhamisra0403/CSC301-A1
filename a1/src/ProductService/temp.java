import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class temp {

    // In-Memory Database: Key = Product ID, Value = JSON String
    private static final Map<Integer, String> productDb = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ProductService <config.json>");
            System.exit(1);
        }

        // 1. Read Config
        String configContent = new String(Files.readAllBytes(Paths.get(args[0])));
        int port = JsonUtils.getServicePort(configContent, "ProductService");
        String ip = JsonUtils.getServiceIp(configContent, "ProductService");

        if (port == -1 || ip == null) {
            System.err.println("Error: Could not parse ProductService config.");
            System.exit(1);
        }

        // 2. Start Server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        server.createContext("/product", new ProductHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("ProductService started on " + ip + ":" + port);
    }

    static class ProductHandler implements HttpHandler {
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
            // Path format: /product/<id>
            String[] parts = path.split("/");
            if (parts.length != 3) {
                sendResponse(t, 400, "{}");
                return;
            }

            try {
                int id = Integer.parseInt(parts[2]);
                if (productDb.containsKey(id)) {
                    sendResponse(t, 200, productDb.get(id));
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
                if (productDb.containsKey(id)) {
                    sendResponse(t, 409, "{\"status\": \"Product already exists\"}");
                    return;
                }

                String name = JsonUtils.parseString(body, "name");
                String description = JsonUtils.parseString(body, "description");
                Double price = JsonUtils.parseDouble(body, "price");
                Integer quantity = JsonUtils.parseInt(body, "quantity");

                if (name == null || price == null || quantity == null) {
                    sendResponse(t, 400, "{\"status\": \"Missing fields\"}");
                    return;
                }

                // VALIDATION: Negative Check
                if (price < 0 || quantity < 0) {
                    sendResponse(t, 400, "{\"status\": \"Invalid Request: Negative values\"}");
                    return;
                }

                String prodJson = String.format(
                    "{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}",
                    id, name, (description != null ? description : ""), price, quantity
                );

                productDb.put(id, prodJson);
                // Consistent Success Message
                sendResponse(t, 200, "{\"status\": \"Success\"}");
            }
            // --- UPDATE ---
            else if (command.equalsIgnoreCase("update")) {
                if (!productDb.containsKey(id)) {
                    sendResponse(t, 404, "{\"status\": \"Not Found\"}");
                    return;
                }

                // Parse existing data
                String currentJson = productDb.get(id);
                String currentName = JsonUtils.parseString(currentJson, "name");
                String currentDesc = JsonUtils.parseString(currentJson, "description");
                Double currentPrice = JsonUtils.parseDouble(currentJson, "price");
                Integer currentQty = JsonUtils.parseInt(currentJson, "quantity");

                // Check for updates in request
                String newName = JsonUtils.parseString(body, "name");
                String newDesc = JsonUtils.parseString(body, "description");
                Double newPrice = JsonUtils.parseDouble(body, "price");
                Integer newQty = JsonUtils.parseInt(body, "quantity");

                if (newName != null) currentName = newName;
                if (newDesc != null) currentDesc = newDesc;
                
                // Validate Updates
                if (newPrice != null) {
                    if (newPrice < 0) { 
                        sendResponse(t, 400, "{\"status\": \"Invalid Request: Negative Price\"}"); 
                        return; 
                    }
                    currentPrice = newPrice;
                }
                if (newQty != null) {
                    if (newQty < 0) { 
                        sendResponse(t, 400, "{\"status\": \"Invalid Request: Negative Quantity\"}"); 
                        return; 
                    }
                    currentQty = newQty;
                }

                String updatedJson = String.format(
                    "{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}",
                    id, currentName, currentDesc, currentPrice, currentQty
                );

                productDb.put(id, updatedJson);
                sendResponse(t, 200, "{\"status\": \"Success\"}");
            }
            // --- DELETE ---
            else if (command.equalsIgnoreCase("delete")) {
                if (!productDb.containsKey(id)) {
                    sendResponse(t, 404, "{\"status\": \"Not Found\"}");
                    return;
                }
                
                String storedJson = productDb.get(id);
                String storedName = JsonUtils.parseString(storedJson, "name");
                Double storedPrice = JsonUtils.parseDouble(storedJson, "price");
                Integer storedQty = JsonUtils.parseInt(storedJson, "quantity");
                
                String reqName = JsonUtils.parseString(body, "name");
                Double reqPrice = JsonUtils.parseDouble(body, "price");
                Integer reqQty = JsonUtils.parseInt(body, "quantity");

                // Verification: Fields must match to allow delete
                boolean matches = (reqName != null && reqName.equals(storedName)) &&
                                  (reqPrice != null && Math.abs(reqPrice - storedPrice) < 0.001) &&
                                  (reqQty != null && reqQty.equals(storedQty));

                if (matches) {
                    productDb.remove(id);
                    sendResponse(t, 200, "{\"status\": \"Success\"}");
                } else {
                    sendResponse(t, 400, "{\"status\": \"Mismatch\"}"); 
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
    }
}