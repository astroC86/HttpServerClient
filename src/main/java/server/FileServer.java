package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class FileServer {

    public static void main(String[] args) throws IOException {

        int portNumber = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(portNumber);

        AtomicInteger threadCount = new AtomicInteger(0);
        Heuristic heuristic = new Heuristic(threadCount, 200, 5000);
        //noinspection InfiniteLoopStatement
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new FileServerThread(
                    clientSocket,
                    "Thead" + UUID.randomUUID().toString(),
                    () -> threadCount.decrementAndGet(),
                    heuristic
            ).start();
            threadCount.incrementAndGet();
        }

    }
}