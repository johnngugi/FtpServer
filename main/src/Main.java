import ftp.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            Server server = new Server();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
