package ftp;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RequestHandler {

    private final SocketChannel socket;
    private String userName;
    private Map<String, Consumer<String>> processFunctions = new HashMap<>();

    public RequestHandler(SocketChannel socket, String directory) {
        this.socket = socket;
        processFunctions.put("USER", this::processUser);
        processFunctions.put("PASS", this::processPassword);
        processFunctions.put("AUTH", this::processSecurityExtension);
        processFunctions.put("PWD", this::processWorkingDirecory);
    }

    public void processCommand(String command, String parameter) throws IOException {
        command = command.toUpperCase();
        try {
            processFunctions.get(command).accept(parameter);
        } catch (NullPointerException e) {
            FtpUtil.println(socket, "502 not implemented");
            e.printStackTrace();
        }
    }

    private void processWorkingDirecory(String parameter) {

    }

    private void processSecurityExtension(String parameter) {
        try {
            if (parameter.equals("TLS")) {
                FtpUtil.println(socket, "502 " + parameter + " not implemented");
            }

            if (parameter.equals("SSL")) {
                FtpUtil.println(socket, "502 " + parameter + " not implemented");
            }
        } catch (IOException e) {
            System.out.println("Error processing security extension");
            e.printStackTrace();
        }
    }

    private void processPassword(String parameter) {
        try {
            if (this.userName == null) {
                FtpUtil.println(socket, "503 Bad sequence of commands. Send USER first.");
            }
            FtpUtil.println(socket, "230 User " + this.userName + " logged in.");
        } catch (IOException e) {
            System.out.println("Error processing password");
            e.printStackTrace();
        }
    }

    private void processUser(String parameter) {
        try {
            if (parameter.equals("Anonymous")) {
                this.userName = parameter;
                FtpUtil.println(socket, "331 Password should be email address");
                return;
            }
            this.userName = parameter;
            FtpUtil.println(socket, "331 Password required for " + parameter);
        } catch (IOException e) {
            System.out.println("Error with processing user");
            e.printStackTrace();
        }
    }
}
