package ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class DataConnection implements Runnable {
    protected InetSocketAddress address;
    protected SocketChannel channel;
    private Thread thread = null;

    private List<DataConnectionListener> listeners =
            Collections.synchronizedList(new ArrayList<>());
    private boolean isNegotiable = false;
    private final Object lock = new Object();
    private boolean notified = false;

    private ByteBuffer toWrite = null;
    private File fileSend = null;
    private File fileReceive = null;
    private long offset = 0L;

    static DataConnection createPassive() throws IOException {
        return new PassiveConnection();
    }

    void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    protected abstract void doNegotiate() throws IOException;

    @Override
    public void run() {
        FileChannel file = null;
        try {
            isNegotiable = false;
            doNegotiate();
            isNegotiable = true;
            for (DataConnectionListener l : listeners)
                l.actionNegotiated(true);

            synchronized (lock) {
                if (!notified) {
//					System.out.println( "DEBUG: lock.wait()" );
                    lock.wait(1000 * 8);
                }
            }

            for (DataConnectionListener l : listeners)
                l.transferStarted();

            if (toWrite != null) {
                while (toWrite.hasRemaining())
                    channel.write(toWrite);
            }

            if (fileSend != null) {
                ByteBuffer buf = ByteBuffer.allocateDirect(16384);
                file = new FileInputStream(fileSend).getChannel();
                file.position(offset);
                FtpUtil.readWriteOperation(file, channel, buf);
            }

            if (fileReceive != null) {
                ByteBuffer buf = ByteBuffer.allocateDirect(16384);
                file = new FileOutputStream(fileReceive).getChannel();
                FtpUtil.readWriteOperation(channel, file, buf);
            }

            for (DataConnectionListener l : listeners)
                l.transferCompleted(false);

        } catch (InterruptedException e) {
            System.out.println("Interrupted exception");
            e.printStackTrace();
            FtpUtil.setTransferComplete(isNegotiable, listeners);
        } catch (Exception e) {
            e.printStackTrace();
            FtpUtil.setTransferComplete(isNegotiable, listeners);
        } finally {
            FtpUtil.releaseChannelResource(file);
            stop();
        }
    }

    public void stop() {
        FtpUtil.releaseChannelResource(channel);

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    void setFileOffset(long offset) {
        this.offset = offset;
    }

    void addDataConnectionListener(DataConnectionListener l) {
        if (!listeners.contains(l))
            listeners.add(l);
    }

    String getAddressAsString() {
        int port = address.getPort();
        String[] ips = address.getAddress().getHostAddress().split("\\.");
        return ips[0] + "," + ips[1] + "," + ips[2] + "," + ips[3] +
                "," + (port / 256) + "," + (port % 256);
    }

    void send(String msg, boolean isUTF8) throws IOException {
        this.toWrite = ByteBuffer.wrap(msg.getBytes(
                isUTF8 ? "UTF-8" : System.getProperty("client.file.encoding")));
        synchronized (lock) {
            lock.notify();
        }
        this.notified = true;
        // System.out.println( "DEBUG: lock.notify()" );
    }

    void sendFile(File f) {
        this.toWrite = null;
        this.fileSend = f;
        synchronized (lock) {
            lock.notify();
        }
        this.notified = true;
    }

    void storeFile(File f) {
        this.toWrite = null;
        this.fileReceive = f;
        synchronized (lock) {
            lock.notify();
        }
        this.notified = true;
    }
}
