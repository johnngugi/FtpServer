package ftp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.util.List;

class FtpUtil {

    static final String FTP_COMMAND_NOOP = "NOOP";
    static final String FTP_COMMAND_MDTM = "MDTM";
    static final String FTP_COMMAND_EPRT = "EPRT";
    static final String FTP_COMMAND_NLST = "NLST";
    static final String FTP_COMMAND_RMD = "RMD";
    static final String FTP_COMMAND_MKD = "MKD";
    static final String FTP_COMMAND_PORT = "PORT";
    static final String FTP_COMMAND_QUIT = "QUIT";
    static final String FTP_COMMAND_SIZE = "SIZE";
    static final String FTP_COMMAND_DELE = "DELE";
    static final String FTP_COMMAND_STOR = "STOR";
    static final String FTP_COMMAND_REST = "REST";
    static final String FTP_COMMAND_OPTS = "OPTS";
    static final String FTP_COMMAND_FEAT = "FEAT";
    static final String FTP_COMMAND_USER = "USER";
    static final String FTP_COMMAND_PASS = "PASS";
    static final String FTP_COMMAND_AUTH = "AUTH";
    static final String FTP_COMMAND_PWD = "PWD";
    static final String FTP_COMMAND_TYPE = "TYPE";
    static final String FTP_COMMAND_PASV = "PASV";
    static final String FTP_COMMAND_LIST = "LIST";
    static final String FTP_COMMAND_CWD = "CWD";
    static final String FTP_COMMAND_CDUP = "CDUP";
    static final String FTP_COMMAND_SYST = "SYST";
    static final String FTP_COMMAND_RETR = "RETR";

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
