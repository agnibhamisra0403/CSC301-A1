package OrderService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonHelpers {
    /***
     * return a string value for the given key. 
     * 
     * parseString(json, "username") -> "username1234"
     * @param json the json string
     * @param key the key for the key value pair
     * @return String of the value
     */
    public static String parseString (String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);

        }
        return null;
    }


}
