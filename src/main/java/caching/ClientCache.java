package caching;

import data.HttpRequest;
import data.HttpResponse;
import data.HttpVerb;
import data.builders.HttpResponseBuilder;
import data.parsers.HttpResponseParser;
import exceptions.MessageParsingException;
import handlers.TransferEncodingHandlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

import static utils.Content.readLine;
import static utils.Content.typeExtension;

public class ClientCache {
    private final HashMap<String, HttpResponse> cacheIndex =  new HashMap<>();

    public Optional<HttpResponse> process(HttpRequest request, InputStream in, OutputStream out) throws IOException, MessageParsingException {
        boolean cache = false;
        var resource = request.getPath();
        Optional<String> hostOption = request.lookup("host");
        if(hostOption.isPresent()) resource += "/" + hostOption.get();
        if (request.getVerb().equals(HttpVerb.GET)){
            if (cacheIndex.containsKey(resource)) {
                var res     =  cacheIndex.get(resource);
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
                res.send(tempOut);
                in =  new ByteArrayInputStream(tempOut.toString().getBytes());
            } else {
                cache = true;
                request.send(out);
            }
        } else request.send(out);
        var req = getResponse(in, request);
        if (cache && req.isPresent()) cacheIndex.put(resource,req.get());
        return req;
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
            if (contentTypeOptional.isEmpty())
                throw new MissingFormatArgumentException("Headers don't include Content-Type.");
            int contentLength;
            try   {contentLength = Integer.parseInt(contentLengthOptional.get());}
            catch (NumberFormatException e) {
                throw new NumberFormatException("Malformed Content-Length.");
            }
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
        if (body.length != 0) {
            for (var bh: request.getBodyHandlers()){
                //noinspection unchecked
                bh.apply(body);
            }
        }
        parsedResponse = new HttpResponseBuilder(parsedResponse,body).build();
        return Optional.of(parsedResponse);
    }
}
