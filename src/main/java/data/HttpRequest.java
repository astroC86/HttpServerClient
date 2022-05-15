package data;

import caching.ClientCache;
import caching.LazySocket;
import exceptions.MessageParsingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class HttpRequest {
    private static final String CLRF = "\r\n";
    private byte[] body;
    private String path;
    private HttpVerb verb;
    private int majorVersion;
    private int minorVersion;
    private Map<String, String> headers;
    private List<Function> bodyHandlers = new ArrayList<>();

    public HttpRequest(String path, Map<String, String> headers, int majorVersion, int minorVersion, HttpVerb verb) {
        this.verb         = verb;
        this.path         = path;
        this.headers      = headers;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public HttpRequest(String path, HashMap<String, String> headers, int major, int minor, HttpVerb verb, byte[] body) {
        this(path,headers,major,minor,verb);
        this.body = body;
    }

    public HttpRequest(String path, Map<String, String> headers, HttpVersion version, HttpVerb verb) {
        this.verb         = verb;
        this.path         = path;
        this.headers      = headers;
        this.majorVersion = version.major();
        this.minorVersion = version.minor();
    }

    public HttpRequest(String path, HashMap<String, String> headers, HttpVersion version, HttpVerb verb, byte[] body) {
        this(path,headers,version,verb);
        this.body = body;
    }

    public Optional<String> lookup(String key){
        key = key.toLowerCase();
        Optional<String> value = Optional.empty();
        if (headers.containsKey(key)) {
            value = Optional.of(headers.get(key));
        }
        return value;
    }

    public void send(OutputStream out) throws IOException {
        out.write((verb.name() +" "+path+" HTTP/"+ majorVersion+"."+minorVersion+CLRF).getBytes(StandardCharsets.US_ASCII));
        for (var kv: headers.entrySet()) {
            out.write((kv.getKey()+": "+kv.getValue()+CLRF).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(CLRF.getBytes(StandardCharsets.US_ASCII));
        if(body!=null)
            out.write(body);
    }

    public Optional<HttpResponse> send(ClientCache cache, LazySocket socket) throws IOException, MessageParsingException {
        return cache.process(this,socket);
    }



    public boolean persists(){
        var persists = (majorVersion == 1 && minorVersion == 1) || majorVersion > 1;
        if (headers.containsKey("connection")){
            var keepAlive = headers.get("connection");
            return keepAlive.equals("keep-alive");
        }
        return persists;
    }

    public int headerCount(){
        return headers.size();
    }

    public String getPath() {
        return path;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public HttpVerb getVerb() {
        return verb;
    }

    public List<Function> getBodyHandlers(){
        return bodyHandlers;
    }

    public void addHandlers(List<Function> bodyHandlers) {
        this.bodyHandlers = bodyHandlers;
    }

    public HashMap<String,String> cloneHeaders(){return (HashMap<String, String>) Map.copyOf(headers);}

    @Override
    public String toString() {
        return "HTTP Request{" +
                "HTTP/" + getMajorVersion() +
                "." + getMinorVersion() +
                ", path='" + path + '\'' +
                ", headers=" + headers +
                ", verb=" + verb +
                '}';
    }
}
