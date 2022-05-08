package data;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

public class HttpRequest {
    private static final String CLRF = "\r\n";
    private String path;
    private Map<String, String> headers;
    private int majorVersion;
    private int minorVersion;
    private HttpVerb verb;
    private byte[] body;
    private List<Function> bodyHandlers = new ArrayList<>();

    /**
     *
     * @param path
     * @param headers
     * @param majorVersion
     * @param minorVersion
     * @param verb
     */
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

    public Optional<String> lookup(String key){
        key = key.toLowerCase();
        Optional<String> value = Optional.empty();
        if (headers.containsKey(key)) {
            value = Optional.of(headers.get(key));
        }
        return value;
    }

    public int headerCount(){
        return headers.size();
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

    public void addHandlers(List<Function> bodyHandlers) {
        this.bodyHandlers = bodyHandlers;
    }

    public List<Function> getBodyHandlers(){
        return bodyHandlers;
    }

    @Override
    public String toString() {
        return "ParsedMessage{" +
                "path='" + path + '\'' +
                ", headers=" + headers +
                ", majorVersion=" + majorVersion +
                ", minorVersion=" + minorVersion +
                ", verb=" + verb +
                '}';
    }
}
