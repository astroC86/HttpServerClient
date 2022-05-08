package client;

import collections.Pair;
import data.HttpRequest;
import data.HttpResponse;
import data.HttpVersion;
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
    private Logger             logger;
    private OutputStream       out;
    private InputStream        in;
    private Socket             clientSocket;


    public HttpClientThread(Queue<Pair<HttpRequest,Integer>> requests) throws IOException {
        this.logger       = Logger.getLogger("Client Thread");
        this.logger.setLevel(Level.ALL);
        while (!requests.isEmpty()){
            var top = requests.poll();
            var req = top.x;
            var hostOptional = req.lookup("Host");
            if (hostOptional.isEmpty()){
                System.err.println("Invalid Request, Host not defined.");
                continue;
            }
            clientSocket = new Socket(hostOptional.get(),top.y);
            send(top.x);
            in.close();
            out.close();
            clientSocket.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void send(HttpRequest request) {
        try {
            this.out          = clientSocket.getOutputStream();
            this.in           = clientSocket.getInputStream();

            request.send(this.out);

            ArrayList<String> lines = new ArrayList<>();
            while (true) {
                String line = readLine(in);
                if(line == null) return;
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
                    if(Arrays.stream(codings).anyMatch(s -> s.equals("gzip")     ||
                                                            s.equals("compress") ||
                                                            s.equals("deflate")) ){
                        throw new RuntimeException("Does not support exception");
                    } else if (Arrays.asList(codings).contains("chunked")){
                        body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
                    }
                }else if (transferEncoding.equals("chunked")){
                    body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
                }
            }
            if(body.length != 0) {
                for (var bh: request.getBodyHandlers())//noinspection unchecked
                    bh.apply(body);
            }
        } catch (IOException | MessageParsingException e) {
            e.printStackTrace();
        }
    }
}
