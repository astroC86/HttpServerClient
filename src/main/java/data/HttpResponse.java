package data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpResponse {
    private final String CLRF = "\r\n";
    private final int minor;
    private final int major;
    private byte[] body;
    private final int statusCode;
    private final String statusMessage;
    private final Map<String,String> headers;

    public HttpResponse(int major,int minor, int statusCode, String statusMessage, Map<String, String> headers) {
        this.major = major;
        this.minor = minor;
        this.statusCode    = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
    }

    public HttpResponse(HttpVersion version, int statusCode, String statusMessage,  Map<String, String> headers,byte[] body) {
        switch (version) {
            case HTTP_1_0 -> { major = 1; minor = 0;}
            case HTTP_1_1 -> { minor = 1; major = 1;}
            default -> throw new IllegalArgumentException("Provided an invalid HTTP version");
        }
        this.statusCode    = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
        this.body = body;
    }

    public Optional<String> lookup(String key){
        Optional<String> value = Optional.empty();
        if (headers.containsKey(key)) {
            value = Optional.of(headers.get(key));
        }
        return value;
    }

    public void send(OutputStream out) throws IOException {
        out.write(("HTTP/"+major + "." +minor + " " + statusCode + " " + statusMessage + CLRF).getBytes(StandardCharsets.US_ASCII));
        for (var kv: headers.entrySet()) {
            String key = Arrays.stream(kv.getKey().split("-"))
                    .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                    .collect(Collectors.joining("-"));
            out.write((key + ": " + kv.getValue()+CLRF).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(CLRF.getBytes(StandardCharsets.US_ASCII));
        out.write(this.body);
    }
    
    public boolean persists(){
        var persists = (major == 1 && minor == 1) || major > 1;
        if (headers.containsKey("connection")){
            var keepAlive = headers.get("connection");
            return keepAlive.equals("keep-alive");
        }
        return persists;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public int getMinorVersion() {
        return minor;
    }

    public int getMajorVersion() {
        return major;
    }

    public byte[] getBody() {
        return this.body;
    }

    public Map<String,String> cloneHeaders(){return Map.copyOf(headers);}

    @Override
    public String toString() {
        return "HTTP Response {" +
                "HTTP/" + getMajorVersion() +
                "." + getMinorVersion() +
                ", status='{" + statusCode + ",'"+statusMessage+"'}" +
                ", headers=" + headers +
                '}';
    }
}
