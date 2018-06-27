package ftp;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.NoSuchElementException;
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
     * @param directory     default home directory
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
        FtpUtil.println(channel, "220 welcome to our ftp server");
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
     * Called when the thread is started. Continuously listens for messages from server and passes
     * the server command and parameter to the request handler class
     */
    @Override
    public void run() {
        try {
            while (running) {
                String line = reader.nextLine();
                if (line == null) break;

                String[] requestLine = FtpUtil.split(line);
                String command = requestLine[0];
                String parameter = requestLine[1];

                requestHandler.processCommand(command, parameter);
            }
        }
//        catch (NoSuchElementException e) {
//            System.out.println("* FTPChannel was closed. (" +
//                    channel.socket().getInetAddress() + ")");
//        }
        catch (IOException e) {
            System.out.println("Channel error occurred");
            e.printStackTrace();
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

        FtpUtil.releaseChannelResource(channel);

        running = false;
    }
}
