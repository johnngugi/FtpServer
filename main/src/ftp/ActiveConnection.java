package ftp;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ActiveConnection extends DataConnection {

    ActiveConnection() {
    }

    @Override
    protected void doNegotiate() throws IOException {
        channel = SocketChannel.open();
        channel.configureBlocking(true);
        channel.connect(this.address);
    }
}
