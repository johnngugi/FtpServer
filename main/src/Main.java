import ftp.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            Server server = new Server(9999, "/home/john/Documents"); // todo don't hardcode this
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
