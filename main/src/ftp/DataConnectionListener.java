package ftp;

public interface DataConnectionListener {
    void actionNegotiated(boolean b);

    void transferStarted();

    void transferCompleted(boolean b);
}
