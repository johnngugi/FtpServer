package ftp;

import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.util.Enumeration;

public class PassiveConnection extends DataConnection {


    private ServerSocketChannel socket;

    PassiveConnection() throws IOException {
        InetAddress local = getCurrentIp();
        if (local == null)
            throw new IOException("Can't get local ip address");

        int port = findFreePort();

        this.address = new InetSocketAddress(local, port);
        System.out.println(address.getPort());

        socket = ServerSocketChannel.open();
        socket.configureBlocking(true);
        socket.socket().setSoTimeout(1000 * 10);
        socket.socket().bind(this.address);
    }

    private InetAddress getCurrentIp() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (!local.isLoopbackAddress()) {
                return local;
            }
            Enumeration en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) en.nextElement();
                Enumeration ee = ni.getInetAddresses();
                while (ee.hasMoreElements()) {
                    InetAddress ia = (InetAddress) ee.nextElement();
                    if (!ia.isLoopbackAddress() && ia instanceof Inet4Address) {
                        return ia;
                    }
                }
            }
        } catch (UnknownHostException | SocketException e) {
            System.out.println("Error getting address");
            e.printStackTrace();
        }
        return null;
    }

    private static int findFreePort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore IOException on close()
            }
            return port;
        } catch (IOException e) {
            System.out.println("Couldn't find free port");
            e.printStackTrace();
        }
        return -1;
    }

    protected void doNegotiate() throws IOException {
        super.channel = socket.accept();
    }

    public void stop() {
        super.stop();
        FtpUtil.releaseChannelResource(socket);
    }

}
