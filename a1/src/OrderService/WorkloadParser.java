package OrderService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

public class WorkloadParser {
    private static String orderServiceHttpUrl;

    public static void main(String[] args) {

        if (args.length < 1) {
            // no workload file provided
            System.err.println("Need to provide workload file");
            System.exit(1);
        }

        // arguement index 0 contains the workload file
        String Workload = args[0];

        try {
            // the config file    
            String ConfigJson = new String(Files.readAllBytes(Paths.get("Config.json"))); 

            String ip = parseConfigFile(ConfigJson, "OrderService", "ip");
            String port = parseConfigFile(ConfigJson, "OrderService", "port");

            if (ip == null | port == null) {
                ip = "127.0.0.1";
                port = "14003";
                System.out.println("Warning: Parsing Config file failed. Using default " + ip + ":" + port);
            }

            // the URL for the HTTP requests
            orderServiceHttpUrl = "http://" + ip + ":" + port;
            System.out.println("Targeting OrderService at: " + orderServiceHttpUrl);

            workloadAction(Workload);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void workloadAction(String file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // current line being iterated
            String currentLine;
            
            while ((currentLine = br.readLine()) != null) {
                currentLine = currentLine.trim();

                // skip the lines, comments, and metadata
                if (currentLine.isEmpty() || currentLine.startsWith("#") || currentLine.startsWith("[")) {continue;}

                // tokenize the input based on spaces
                String[] tokens = currentLine.split("\\s+");
                String service = tokens[0].toUpperCase();
                String command = tokens[1].toLowerCase();

                // HTTP variables
                String endpoint = "";
                String jsonData = ""; 
                String method = ""; 

                // USER Commands
                if (service.equals("USER")) {
                    endpoint = "/user";
                    method = "POST";

                    // create
                    if (command.equals("create")) {
                        method = "POST";
                        jsonData = String.format("{\"command\":\"create\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}", tokens[2], tokens[3], tokens[4], tokens[5]);
                    }

                    // get
                    else if (command.equals("get")) {
                        method = "GET";
                        endpoint = "/user/" + tokens[2];

                    }

                    // update
                    else if (command.equals("update")) {
                        method = "POST";
                        jsonData = updateJson(tokens);
                    }

                    // delete
                    else if (command.equals("delete")) {
                        method = "POST";
                        jsonData = String.format("{\"command\":\"delete\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}", tokens[2], tokens[3], tokens[4], tokens[5]);
                    }
                }

                // Product commands
                else if (service.equals("PRODUCT")) {
                    endpoint = "/product";

                     // create
                    if (command.equals("create")) {
                        method = "POST";
                        jsonData = String.format("{\"command\":\"create\", \"id\":%s, \"name\":\"%s\", \"description\":\"%s\", \"price\":%s, \"quantity\":%s}", tokens[2], tokens[3], tokens[3], tokens[4], tokens[5]);
                    }

                    // info
                    else if (command.equals("info")) {
                        method = "GET";
                        endpoint = "/user/" + tokens[2];

                    }

                    // update
                    else if (command.equals("update")) {
                        method = "POST";
                        jsonData = updateJson(tokens);
                    }

                    // delete
                    else if (command.equals("delete")) {
                        method = "POST";
                        jsonData = String.format("{\"command\":\"delete\", \"id\":%s, \"username\":\"%s\", \"email\":\"%s\", \"password\":\"%s\"}", tokens[2], tokens[3], tokens[4], tokens[5]);
                    }
                }

                // Order commands
                else if (service.equals("ORDER")) {
                    endpoint = "/order"; 
                    method = "POST";
                    
                    // place
                    if (command.equals("place")) {
                        jsonData = String.format("{\"command\":\"place order\", \"product_id\":%s, \"user_id\":%s, \"quantity\":%s}", tokens[2], tokens[3], tokens[4]);
                    }
                }

                // place the request only if a valid endpoint is present
                if (!endpoint.isEmpty()) {
                    placeRequest(method, orderServiceHttpUrl + endpoint, jsonData); 
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String parseConfigFile(String json, String service, String key) {
        Pattern pattern = Pattern.compile("\"" + service + "\"\\s*:\\s*\\{[^}]*\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?");

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1); 
        }
        return null;
    }

    private static void placeRequest(String method, String url, String jsonData) {
        try {
            // URL object and opening connection
            URI uri = new URI(url);
            URL Url = uri.toURL();
            HttpURLConnection connection = (HttpsURLConnection) Url.openConnection();

            // set the HTTP method to either get or post
            connection.setRequestMethod(method);

            // set content type to JSON for all requests
            connection.setRequestProperty("Content-Type", "application/json");

            // if the method is post
            if (method.equals("POST")) {
                if (!jsonData.isEmpty()) {
                    connection.setDoOutput(true);

                    try (OutputStream os = connection.getOutputStream()){
                        byte[] inputBytesList = jsonData.getBytes("utf-8");
                        os.write(inputBytesList, 0, inputBytesList.length); 

                    }

                }
            }

            // get the HTTP status code
            int code = connection.getResponseCode();

            // print the correct output to the system
            System.out.println(method + " " + url + " [" + code + "]");

        } catch (Exception e) {
            System.out.println("Failed connection");
        }
    }

    private static String updateJson(String[] tokens) {
        StringBuilder json = new StringBuilder();
        json.append("{\"command\":\"update").append("\", \"id\":").append(tokens[2]);

        for (int i = 3; i < tokens.length; i++) {
            String[] keyValuePairs = tokens[i].split(":");

            // append the key value pairs with a colon and quotation marks (when necessary)
            if (keyValuePairs.length == 2) {
                json.append(", \"").append(keyValuePairs[0]).append("\":");

                if (keyValuePairs[0].equals("quantity") || keyValuePairs[0].equals("price")) {
                    json.append(keyValuePairs[1]);
                }

                else {
                    json.append("\"").append(keyValuePairs[1]).append("\"");
                }
            }

        }
        return json.append("}").toString(); 
    }

}
