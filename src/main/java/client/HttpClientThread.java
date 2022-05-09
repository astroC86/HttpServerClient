package client;

import collections.Pair;
import data.HttpRequest;
import data.HttpResponse;
import data.parsers.HttpResponseParser;
import exceptions.MessageParsingException;
import handlers.TransferEncodingHandlers;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static utils.Content.readLine;

public class HttpClientThread {
    private Logger          logger;
    private OutputStream    out;
    private InputStream     in;
    private Socket          clientSocket;
    private static int      timeout = 100;
    HashMap<String, Socket> persistantHosts =  new HashMap<>();

    public HttpClientThread(Queue<Pair<HttpRequest,Integer>> requests)  {
        this.logger       = Logger.getLogger("Client Thread");
        this.logger.setLevel(Level.ALL);
        while (!requests.isEmpty()){
            var top = requests.poll();
            var req = top.x;
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
                if (req_persists && persistantHosts.containsKey(socketPath)){
                    var socket  = persistantHosts.get(socketPath);
                    // determine if the host is connected
                    if (!socket.getInetAddress().isReachable(timeout)){
                        socket.close();
                        //close the existing socket and attempt to reconnect
                    }
                } else {
                    //create a new socket
                        clientSocket = new Socket(host, port);
                }
                var persists  = send(req);
                if(persists){
                    if(persistantHosts.containsKey(socketPath))
                        persistantHosts.remove(socketPath);
                    else {
                        clientSocket.setKeepAlive(true);
                        persistantHosts.put(socketPath,clientSocket);
                    }
                } else { //close the socket
                    in.close();
                    out.close();
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, Arrays.toString(e.getStackTrace()).replace(",","\n"));
            }
        }
    }



    private void retry() {
        //TODO
        /*
          1. Initiate a new connection to the server

          2. Transmit the request-headers

          3. Initialize a variable R to the estimated round-trip time to the
             server (e.g., based on the time it took to establish the
             connection), or to a constant value of 5 seconds if the round-
             trip time is not available.

          4. Compute T = R * (2**N), where N is the number of previous
             retries of this request.

          5. Wait either for an error response from the server, or for T
             seconds (whichever comes first)

          6. If no error response is received, after T seconds transmit the
             body of the request.

          7. If client sees that the connection is closed prematurely,
             repeat from step 1 until the request is accepted, an error
             response is received, or the user becomes impatient and
             terminates the retry process.
         */
    }

    @SuppressWarnings("unchecked")
    private boolean send(HttpRequest request) {
        boolean persisting = false;

        try {
            this.out          = clientSocket.getOutputStream();
            this.in           = clientSocket.getInputStream();

            request.send(this.out);

            ArrayList<String> lines = new ArrayList<>();
            while (true) {
                String line = readLine(in);
                if(line == null) return false;
                if(line.isEmpty()) break;
                lines.add(line);
            }
            logger.log(Level.INFO, "input:\n  {0}", String.join("\n  ", lines));

            byte[] body = new byte[0];
            HttpResponse parsedResponse = HttpResponseParser.parse(lines);
            Optional<String> contentLengthOptional    = parsedResponse.lookup("content-length");
            Optional<String> contentTypeOptional      = parsedResponse.lookup("content-type");
            Optional<String> transferEncodingOptional = parsedResponse.lookup("transfer-encoding");
            if(transferEncodingOptional.isEmpty()) {
                if (contentLengthOptional.isEmpty())
                    throw new MissingFormatArgumentException("Headers don't include Content-Length.");
                if (contentTypeOptional.isEmpty())
                    throw new MissingFormatArgumentException("Headers don't include Content-Type.");

                int contentLength;
                try {
                    contentLength = Integer.parseInt(contentLengthOptional.get());
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("Malformed Content-Length.");
                }
              body = in.readNBytes(contentLength);
            } else {
                var transferEncoding = transferEncodingOptional.get();
                if(transferEncoding.contains(",")){
                    var codings = transferEncoding.split(",");
                    if(Arrays.stream(codings).anyMatch(s -> s.equals("gzip") || s.equals("compress") || s.equals("deflate")) ){
                        throw new RuntimeException("Does not support exception");
                    } else if (Arrays.asList(codings).contains("chunked")){
                        body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
                    }
                } else if (transferEncoding.equals("chunked")){
                    body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
                } else {
                  throw new RuntimeException("Unsupported Transfer-Encoding Value");
                }
            }
            if(body.length != 0) {
                for (var bh: request.getBodyHandlers())//noinspection unchecked
                    bh.apply(body);
            }
            persisting = parsedResponse.persists();
        } catch (IOException | MessageParsingException e) {
            e.printStackTrace();
        }
        return persisting;
    }
}
