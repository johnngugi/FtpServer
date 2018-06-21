package ftp;

import java.nio.channels.SocketChannel;

/**
 * Control channel class
 */
public class ControlChannel implements Runnable {
    private Thread thread;

    public ControlChannel(SocketChannel socketChannel) {

    }

    public void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public void run() {

    }
}
