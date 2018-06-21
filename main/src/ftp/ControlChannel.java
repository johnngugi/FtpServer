package ftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

/**
 * Control channel class
 */
public class ControlChannel implements Runnable {
    private final RequestHandler requestHandler;
    private final String directory;
    private SocketChannel channel;
    private final Scanner reader;
    private Thread thread;
    private boolean running;

    /**
     * Constructor class that initialises field method and calls onConnect() method
     *
     * @param socketChannel socketChannel
     * @param directory default home directory
     * @throws IOException thrown by onConnect() method
     */
    public ControlChannel(SocketChannel socketChannel, String directory) throws IOException {
        running = false;
        this.directory = directory;
        this.channel = socketChannel;
        this.reader = new Scanner(channel);
        this.requestHandler = new RequestHandler(this.channel, this.directory);

        onConnect();
    }

    /**
     * Writes a response to the server to confirm connection
     *
     * @throws IOException thrown by println method
     */
    private void onConnect() throws IOException {
        println("220 welcome to our ftp server");
    }

    /**
     * Attaches this class to a new thread and starts the thread
     */
    public void start() {
        if (thread == null) {
            thread = new Thread(this);
            running = true;
            thread.start();
        }
    }

    /**
     * Writes to the socket channel
     *
     * @param msg message to be written
     * @throws IOException thrown when writing to the socket channel
     */
    private void println(String msg) throws IOException {
        System.out.println("<= " + msg);
        ByteBuffer buf = ByteBuffer.wrap((msg + "\r\n").getBytes("UTF-8"));
        while (buf.hasRemaining()) {
            if (channel == null) break;
            channel.write(buf);
        }
    }

    /**
     * Called when the thread is started. Continously listens for messages from server and passes
     * the server command and parameter to the request handler class
     */
    @Override
    public void run() {
        try {
            while (running) {
                String line = reader.nextLine();
                if (line == null) break;

                String command = null;
                String parameter = null;

                int i = line.indexOf(' ');
                if (i != -1) {
                    command = line.substring(0, i);
                    parameter = line.substring(i).trim();
                } else {
                    command = line;
                }
                requestHandler.processCommand(command, parameter);
            }
        } finally {
            stop();
        }
    }

    /**
     * Interrupts the thread and closes the channel. Also set the running field to false
     */
    private void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            channel = null;
        }

        running = false;
    }
}
