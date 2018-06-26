package ftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.util.List;

public class FtpUtil {

    static String[] split(String line) {
        String command;
        String parameter = "";
        int i = line.indexOf(' ');
        if (i != -1) {
            command = line.substring(0, i);
            parameter = line.substring(i).trim();
        } else {
            command = line;
        }
        return new String[]{command, parameter};
    }

    /**
     * Writes a message to the socket channel
     *
     * @param channel the channel to be written to
     * @param msg     the message to be written
     * @throws IOException thrown by channel.write() method call
     */
    static void println(SocketChannel channel, String msg) throws IOException {
        System.out.println("<= " + msg);
        ByteBuffer buf = ByteBuffer.wrap((msg + "\r\n").getBytes("UTF-8"));
        while (buf.hasRemaining()) {
            if (channel == null) break;
            channel.write(buf);
        }
    }

    static void readWriteOperation(ByteChannel readableChannel, ByteChannel writableChannel, ByteBuffer buffer)
            throws IOException {
        while (true) {
            buffer.clear();
            int readlen = readableChannel.read(buffer);
            if (readlen < 1)
                break;
            buffer.flip();
            while (buffer.hasRemaining())
                writableChannel.write(buffer);
        }
    }

    static void setTransferComplete(boolean isNegotiable, List<DataConnectionListener> listeners) {
        if (!isNegotiable) {
            for (DataConnectionListener l : listeners)
                l.actionNegotiated(false);
        } else {
            for (DataConnectionListener l : listeners)
                l.transferCompleted(true);
        }
    }

    static void releaseChannelResource(AbstractInterruptibleChannel resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            resource = null;
        }
    }

}
