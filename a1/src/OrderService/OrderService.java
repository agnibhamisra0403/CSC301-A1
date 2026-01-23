package OrderService;

import java.nio.file.Files;
import java.nio.file.Paths;

public class OrderService {
    private static String IscsUrl;

    public static void main(String[] args) {
        // make sure a config file was passed in
        if (args.length < 1) {
            System.err.println("Need config file as arguement");
            System.exit(1);
        }

        String config = new String(Files.readAllBytes(Paths.get(args[0])));
        
        int port = JsonUtils.getServicePort(config, "OrderService");

        



    }
}
