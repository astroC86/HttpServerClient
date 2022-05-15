package client;

import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import data.parsers.HttpResponseParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static utils.Content.readLine;

public class SocketFactory {
    private  final int      retries = 5;
    private  final int      timeout = 100;
    private  final HashMap<String, Socket> persistentHosts =  new HashMap<>();
    private  final Logger logger;

    public SocketFactory(Logger logger) {
        this.logger = logger;
    }

    public  Optional<Socket> getOrCreateSocket(String host, int port, boolean persistent) throws IOException {
        var socketPath   = host +":"+ port;
        if (persistentHosts.containsKey(socketPath)){
            var socket  = persistentHosts.get(socketPath);
            // Determine if the host is connected ?
            if (!socket.getInetAddress().isReachable(timeout) ){
                socket.close();
                // Close the existing socket and attempt to reconnect and send
                socket = retry(host,port);
                if(socket == null){
                    logger.log(Level.INFO,"Failed to reach to an existing socket of "+host+".");
                    // Otherwise continue
                    persistentHosts.remove(socketPath);
                    return Optional.empty();
                }
            }
            if(persistent) {
                persistentHosts.put(socketPath,socket);
                socket.setKeepAlive(true);
            }
            return Optional.of(socket);
        } else {// Create a new socket
            var socket  =  new Socket(host, port);
            if(persistent) {
                socket.setKeepAlive(true);
                persistentHosts.put(socketPath,socket);
            }
             return Optional.of(socket);
        }
    }

    public void remove(String host, int port) throws IOException {
        var socketPath     = host +":"+ port;
        if (persistentHosts.containsKey(socketPath)){
            Socket socket  = persistentHosts.remove(socketPath);
            socket.close();
        }
    }

    private boolean isSocketAlive(String host,int port) {
        boolean isAlive = false;
        try (Socket soc = new Socket()) {
            soc.connect(new InetSocketAddress(host, port), timeout);
            return true;
        }catch (SocketTimeoutException exception) {
            logger.log(Level.SEVERE,"SocketTimeoutException " + host + ":" + port + ". " + exception.getMessage());
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "IOException - Unable to connect to " + host + ":" + port + ". " + exception.getMessage());
        }
        return isAlive;
    }

    private  Socket retry(String host, int port) throws IOException {
        double T;
        int N    = 0;
        var executor = Executors.newFixedThreadPool(2);
        do {
            //1. Initiate a new connection to the server
            Socket socket = new Socket(host, port);
            var in = socket.getInputStream();
            var out = socket.getOutputStream();
            //          2. Transmit the request-headers
            new HttpRequestBuilder(HttpVerb.POST, HttpVersion.HTTP_1_1)
                    .withHeader("Host", host)
                    .withHeader("Expect", "100-continue")
                    .build().send(out);
            // 3. Initialize a variable R to the estimated round-trip time to the server
            // (e.g., based on the time it took to establish the connection),
            // or to a constant value of 5 seconds if the round-trip time is not available.
            int R = 5;
            // 4. Compute T = R * (2**N), where N is the number of previous retries of this request.
            T = R * Math.pow(2, N);
            var task = new FutureTask<>(
                    () -> {
                        ArrayList<String> lines = new ArrayList<>();
                        while (true) {
                            String line = readLine( in );
                            if (line == null) return null;
                            if (line.isEmpty()) break;
                            lines.add(line);
                        }
                        return HttpResponseParser.parse(lines);
                    }
            );
            // Submit the task to the thread pool
            executor.submit(task);
            try {
                int _T = (int) T;
                // Wait for a result during at most 1 second
                var response = task.get(_T, TimeUnit.SECONDS);
                // 5. Wait either for an error response from the server, or for T seconds (whichever comes first)
                // 6. If no error response is received, after T seconds transmit the body of the request.
                if (response == null) { throw new InterruptedException();}
                var statusCode = response.getStatusCode();
                if(statusCode == 100) {
                    return socket;
                }
            } catch (TimeoutException | ExecutionException | InterruptedException ignored) {}
            // 7. If client sees that the connection is closed prematurely, repeat from step 1 until the request is accepted,
            // an error response is received, or the user becomes impatient and terminates the retry process.
            N += 1;
            in.close();
            out.close();
            socket.close();
            if (retries < N) return null;
        } while (true);
    }

    public boolean has(String host, int port) {
        var socketPath   = host +":"+ port;
        return persistentHosts.containsKey(socketPath);
    }

    public void dispose() throws IOException {
        for (var s: persistentHosts.values()) {
            s.close();
        }
    }
}
