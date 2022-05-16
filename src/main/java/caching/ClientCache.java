package caching;

import data.HttpRequest;
import data.HttpResponse;
import data.HttpVerb;
import data.builders.HttpResponseBuilder;
import data.parsers.HttpResponseParser;
import exceptions.MessageParsingException;
import handlers.TransferEncodingHandlers;

import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

import static utils.Content.readLine;

public class ClientCache {
    private final HashMap<String, HttpResponse> cacheIndex =  new HashMap<>();
    private Logger logger;

    public ClientCache(Logger lgr){
        logger = lgr;
    }

    public boolean isCached(HttpRequest request){
        var resource = request.getPath();
        Optional<String> hostOption = request.lookup("host");
        if(hostOption.isPresent()) resource += "/" + hostOption.get();
        return cacheIndex.containsKey(resource);
    }

    public Optional<HttpResponse> process(HttpRequest request,LazySocket lazySocket) throws IOException, MessageParsingException {
        InputStream  in;
        OutputStream out;

        var          resource     = request.getPath();
        boolean      should_cache = false;
        Optional<String> hostOption = request.lookup("host");
        if(hostOption.isPresent()) resource =  hostOption.get()+resource;
        if (request.getVerb().equals(HttpVerb.GET)) {
            if (cacheIndex.containsKey(resource)) {
                var response     =  cacheIndex.get(resource);
                var tempOut = new OutputStream() {
                        private final StringBuilder string = new StringBuilder();
                        @Override
                        public void write(int b) { this.string.append((char) b );}
                        @Override
                        public void write(byte[] bytes){
                            for (var b:bytes) this.string.append((char) b );
                        }
                        public String toString() { return this.string.toString();}
                    };
                response.send(tempOut);
                in          =  new ByteArrayInputStream(tempOut.toString().getBytes());
                logger.log(Level.INFO,String.format("<Cache HIT> %s",resource));
            } else {
                should_cache = true;
                var socket = lazySocket.get();
                if (socket.isPresent()){
                    in = socket.get().getInputStream();
                    out = socket.get().getOutputStream();
                    request.send(out);
                } else {
                    throw new SocketException(String.format("Could not establish a connection with %s",lazySocket.getSocketAddress()));
                }
            }
        } else {
            var socket = lazySocket.get();
            if (socket.isPresent()){
                in = socket.get().getInputStream();
                out = socket.get().getOutputStream();
                request.send(out);
            }else {
                throw new SocketException(String.format("Could not establish a connection with %s",lazySocket.getSocketAddress()));
            }
        }
        var response = getResponse(in, request);
        if ( should_cache && response.isPresent() &&
                response.get().getStatusCode() == 200 &&
                    request.getVerb() == HttpVerb.GET ) {
            cacheIndex.put(resource,response.get());
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    public Optional<HttpResponse> getResponse(InputStream in, HttpRequest request) throws IOException, MessageParsingException {
        ArrayList<String> lines = new ArrayList<>();
        while (true) {
            String line = readLine(in);
            if(line == null) return Optional.empty();
            if(line.isEmpty()) break;
            lines.add(line);
        }

        byte[] body = new byte[0];
        HttpResponse parsedResponse = HttpResponseParser.parse(lines);

        Optional<String> contentLengthOptional    = parsedResponse.lookup("content-length");
        Optional<String> contentTypeOptional      = parsedResponse.lookup("content-type");
        Optional<String> transferEncodingOptional = parsedResponse.lookup("transfer-encoding");

        if (transferEncodingOptional.isEmpty()) {
            if (contentLengthOptional.isEmpty())
                throw new MissingFormatArgumentException("Headers don't include Content-Length.");

            int contentLength;
            try   {contentLength = Integer.parseInt(contentLengthOptional.get());}
            catch (NumberFormatException e) {throw new NumberFormatException("Malformed Content-Length.");}

            if (contentTypeOptional.isEmpty() && contentLength > 0 )
                throw new MissingFormatArgumentException("Headers don't include Content-Type.");
            body = in.readNBytes(contentLength);
        } else {
            var transferEncoding = transferEncodingOptional.get();
            if (transferEncoding.contains(",")) {
                var codings = transferEncoding.split(",");
                if(Arrays.stream(codings).anyMatch(s -> s.equals("gzip") || s.equals("compress") || s.equals("deflate")) ){
                    throw new RuntimeException("Does not support exception");
                } else if (Arrays.asList(codings).contains("chunked")) {
                    body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
                }
            } else if (transferEncoding.equals("chunked")) {
                body = (byte[]) TransferEncodingHandlers.hndlrs.get("chunked").apply(in);
            } else {
                throw new RuntimeException("Unsupported Transfer-Encoding Value");
            }
        }
        if (body.length != 0 && parsedResponse.getStatusCode() == 200) {
            for (var bh: request.getBodyHandlers()){
                //noinspection unchecked
                bh.apply(body);
            }
        }
        parsedResponse = new HttpResponseBuilder(parsedResponse,body).build();
        return Optional.of(parsedResponse);
    }
}
