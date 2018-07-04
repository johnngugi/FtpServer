import ftp.Server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

public class Main {

    @SuppressWarnings("unchecked")
    private static void loadProperties() throws IOException {
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("main/src/ftp.properties")) {
            prop.load(fis);
        }

        for (Enumeration e = prop.keys(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            System.setProperty(key, prop.getProperty(key));
        }
    }

    public static void main(String[] args) {
        try {
            loadProperties();
            int port = Integer.getInteger("ftp.port", 9999);
            String home = System.getProperty("ftp.home");
            Server server = new Server(port, home);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
