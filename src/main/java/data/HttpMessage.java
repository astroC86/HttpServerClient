package data;

import java.util.Map;
import java.util.Optional;

public class HttpMessage {
    private String path;
    private Map<String, String> headers;
    private int majorVersion;
    private int minorVersion;
    private HttpVerb verb;
    private byte[] message;

    public HttpMessage(String path, Map<String, String> headers, int majorVersion, int minorVersion, HttpVerb verb) {
        this.path = path;
        this.headers = headers;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.verb = verb;
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
        Optional<String> value = Optional.empty();
        if (headers.containsKey(key)) {
            value = Optional.of(headers.get(key));
        }
        return value;
    }
    public int headerCount(){
        return headers.size();
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
