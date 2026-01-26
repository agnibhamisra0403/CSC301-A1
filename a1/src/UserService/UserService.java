package UserService;

import a1.src.Helpers.Helpers; 
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Class UserService to handle user(s) related operations. 
 * 
 * @author misraagn
*/
public class UserService {
    // memory database to store users

    /**
     * The main function for the UserService class
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

        // use the config file to get the user server's ip and port
        String config = new String(Files.readAllBytes(Paths.get(args[0])));
        String ip = Helpers.getIP(config, "UserService");
        int port = Helpers.getPort(config, "UserService");

        

    }
}
