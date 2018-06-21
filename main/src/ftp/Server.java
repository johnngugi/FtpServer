package ftp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server {
    private int port;
    private boolean running;
    private String directory;
    private ServerSocketChannel socket;

    /**
     * Constructor for Server class
     * @throws IOException if error occurs while opening or binding a socket
     */
    public Server(int port, String directory) throws IOException {
        running = false;
        this.port = port;
        this.directory = directory;
        this.socket = ServerSocketChannel.open();
        this.socket.configureBlocking(true);
        this.socket.socket().bind(new InetSocketAddress(port)); // todo don't hardcode port number
    }

    /**
     * Runs the server and starts the control channel on a separate thread
     */
    public void start() {
        if (running) return;
        running = true;
        try {
            while (running) {
                SocketChannel socketChannel = socket.accept();
                new ControlChannel(socketChannel, directory).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    /**
     * Closes the socket and sets it to null
     */
    public void stop() {
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.socket = null;
            running = false;
        }
    }

}
