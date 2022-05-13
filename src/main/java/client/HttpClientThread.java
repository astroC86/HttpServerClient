package client;

import caching.ClientCache;
import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import data.parsers.HttpResponseParser;
import exceptions.MessageParsingException;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static utils.Content.readLine;

public class HttpClientThread {

    private static final int      retries = 5;
    private static final int      timeout = 100;

    private InputStream     in;
    private OutputStream    out;
    private final Logger    logger;
    private Socket          clientSocket;

    private final ClientCache       cache;
    private HashMap<String, Socket> persistentHosts =  new HashMap<>();

    public HttpClientThread(Queue<Pair<HttpRequest,Integer>> requests)  {
        this.logger       = Logger.getLogger("Client Thread");
        this.cache        = new ClientCache(this.logger);
        this.logger.setLevel(Level.ALL);

        while (!requests.isEmpty()){
            var top = requests.poll();
            var req = top.x;
            if(cache.isCached(req)) continue;
            var hostOptional = req.lookup("Host");
            if (hostOptional.isEmpty()){
                logger.log(Level.SEVERE, "Invalid Request, Host not defined.");
                continue;
            }
            var host         = hostOptional.get();
            var port         = top.y;
            var socketPath   = host +":"+port;
            var req_persists = req.persists();
            try {
                if (req_persists && persistentHosts.containsKey(socketPath)){
                    var socket  = persistentHosts.get(socketPath);
                    // determine if the host is connected
                    if (!socket.getInetAddress().isReachable(timeout)){
                        socket.close();
                        //close the existing socket and attempt to reconnect and send
                        socket = retry(host,port);
                        if(socket == null){
                            logger.log(Level.INFO,"Failed to connect to an existing socket of "+host+".");
                            // otherwise continue
                            persistentHosts.remove(socketPath);
                            continue;
                        }
                    }
                } else {
                    //create a new socket
                        clientSocket = new Socket(host, port);
                }
                var persists  = send(req);
                if(persists){
                    if(persistentHosts.containsKey(socketPath))
                        persistentHosts.remove(socketPath);
                    else {
                        clientSocket.setKeepAlive(true);
                        persistentHosts.put(socketPath,clientSocket);
                    }
                } else { //close the socket
                    in.close();
                    out.close();
                    clientSocket.close();
                }
            }catch (ConnectException e){
                logger.log(Level.INFO,"Host "+ host+" refused to connect.");
            } catch (IOException e) {
                logger.log(Level.SEVERE, Arrays.toString(e.getStackTrace()).replace(",","\n"));
            }
        }
    }


    private Socket retry(String host, int port) throws IOException {
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
                    .withHeader("", "").build().send(out);
            // 3. Initialize a variable R to the estimated round-trip time to the server
            // (e.g., based on the time it took to establish the connection),
            // or to a constant value of 5 seconds if the round-trip time is not available.
            int R = 5;
            // 4. Compute T = R * (2**N), where N is the number of previous retries of this request.
            T = R * Math.pow(2, N);
            var task = new FutureTask <> (
                    () -> {
                        ArrayList < String > lines = new ArrayList<>();
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
                var req = task.get(_T, TimeUnit.SECONDS);
                // 5. Wait either for an error response from the server, or for T seconds (whichever comes first)
                // 6. If no error response is received, after T seconds transmit the body of the request.
                var statusCode = req.getStatusCode();
                if(statusCode == 100) {
                    req.send(out); return socket;
                }
            } catch (TimeoutException | ExecutionException | InterruptedException ignored) {}
            // 7. If client sees that the connection is closed prematurely, repeat from step 1 until the request is accepted,
            // an error response is received, or the user becomes impatient and terminates the retry process.
            N += 1;
            in.close();
            out.close();
            socket.close();
            if (HttpClientThread.retries < N) return null;
        } while (true);
    }

    private boolean send(HttpRequest request) {
        boolean persisting = false;
        try {
            this.out = clientSocket.getOutputStream();
            this.in  = clientSocket.getInputStream();
            var parsedResponse = request.send(cache, in, out);
            if (parsedResponse.isEmpty()) return false;
            persisting = parsedResponse.get().persists();
        } catch (IOException e){
            e.printStackTrace();
        } catch (MessageParsingException e) {
            logger.log(Level.SEVERE, "Failed to parse Response for: "+request);
        }
        return persisting;
    }
}
