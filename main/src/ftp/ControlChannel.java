package ftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

/**
 * Control channel class
 */
public class ControlChannel implements Runnable {
    private SocketChannel channel;
    private final Scanner reader;
    private Thread thread;
    private boolean running;

    public ControlChannel(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;
        this.reader = new Scanner(channel);

        onConnect();
    }

    private void onConnect() throws IOException {
        println("220 welcome to our ftp server");
    }

    public void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public void println(String msg) throws IOException {
        System.out.println("<= " + msg);
        ByteBuffer buf = ByteBuffer.wrap((msg + "\r\n").getBytes("UTF-8"));
        while (buf.hasRemaining()) {
            if (channel == null) break;
            channel.write(buf);
        }
    }

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
                processCommand(command, parameter);
            }
        } finally {
            stop();
        }
    }

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
    }

    private void processCommand(String command, String parameter) {
    }
}
