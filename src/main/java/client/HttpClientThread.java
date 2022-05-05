package client;

import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpClientThread extends Thread{
    private Socket serverSocket;
    private Logger logger;

    public HttpClientThread(Socket serverSocket,
                            String threadName) {
        this.serverSocket = serverSocket;
        this.logger       = Logger.getLogger(threadName);

        this.logger.setLevel(Level.ALL);
        this.setName(threadName);
    }

    @Override
    public void run() {

    }
}
