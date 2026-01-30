package Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Utility class containing shared logic for parsing JSON strings and 
 * handling HTTP network requests.
 */
public class Helpers {
    /**
     * Extracts a string value from a JSON string for a given key.
     * @param json the JSON string to parse
     * @param key the key to search for
     * @return the string value associated with the key, or null if not found
     */
    public static String parseString (String json, String key) {

        // look for the pattern of ["key" : "someValue"]
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);

        }
        return null;
    }

    /**
     * Extracts an integer value from a JSON string for a given key.
     * @param json the JSON string to parse
     * @param key the key to search for
     * @return the integer value, or null if invalid or not found
     */
    public static Integer parseInteger(String json, String key) {
        // looks for pattern of ["key" : (int) 1234...]
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([-0-9.]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            
            if (value.contains(".")) {
                return null;
            }
            try {
                // format the value into an int
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extracts a float value from a JSON string for a given key.
     * @param json the JSON string to parse
     * @param key the key to search for
     * @return the float value, or null if invalid or not found
     */
    public static Float parseFloat(String json, String key) {
        // pattern looks for ["key" : (int) 1234.5678]
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+\\.[0-9]+|-?[0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Float.parseFloat(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper function to return the port for the specific service from the config file
     * @param json the config file json data
     * @param service the serivice name
     * @return the port number (int), if invalid return -1
     */
    public static int getPort(String json, String service) {
        Pattern pattern = Pattern.compile("\"" + service + "\"\\s*:\\s*\\{[^}]*\"port\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));

        }
        return -1;
    }

    /**
     * Helper function to return the IP for the specific service from the config file
     * @param json the config file json data
     * @param service the serivice name
     * @return the IP number (int), if invalid return -1
     */
    public static String getIP(String json, String service) {
        Pattern pattern = Pattern.compile("\"" + service + "\"\\s*:\\s*\\{[^}]*\"ip\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1);

        }
        return null;
    }

    /**
     * Sends an HTTP request and returns the status code and response body.
     * @param url the destination URL
     * @param method the HTTP method (GET, POST, etc.)
     * @param body the request body for POST requests
     * @return an Object array where [0] is the status code (int) and [1] is the response (String)
     * @throws IOException if an I/O error occurs during the request
     */
    public static Object[] requestSend(String url, String method, String body) throws IOException {
        // use the url to create a connection
        URL Url = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) Url.openConnection();
        connection.setRequestMethod(method);

        // in the case that this is a post request
        if (body != null && !body.isEmpty() && method.equalsIgnoreCase("post")) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = connection.getOutputStream()){
                os.write(body.getBytes());
            }
        }

        // get the status code indicating success of failure
        int code = connection.getResponseCode();

        // based on the code read the appropriate response body
        InputStream is;
        if (code >= 400) {
            is = connection.getErrorStream();
        }
        else {
            is = connection.getInputStream();
        }

        String response = "";
        if (is != null) {
            Scanner scanner = new Scanner(is).useDelimiter("\\A");

            // Check if stream has content and read it
            if (scanner.hasNext()) {
                response = scanner.next();
            } else {
                response = "";
            }
            
            scanner.close();
        }
        return new Object[]{code, response};
    }


}
