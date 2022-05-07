package data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpResponse {
    private final String CLRF = "\r\n";
    HttpVersion version;

    private Map<String,String> headers;
    private int statusCode;
    private String statusMessage;
    private byte[] body;

    public HttpResponse(int major,int minor, int statusCode, String statusMessage, Map<String, String> headers) {
        this.version       = switch (major){
                                case 1 -> switch (minor){
                                        case 1 -> HttpVersion.HTTP_1_1;
                                        case 0 -> HttpVersion.HTTP_1_0;
                                    default -> throw new IllegalArgumentException("Unexpected value for minor version: " + minor);
                                };
            default -> throw new IllegalArgumentException("Unexpected value for HTTP major version: " + major);
        };
        this.statusCode    = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers;
    }

    public HttpResponse(HttpVersion version, int statusCode, String statusMessage,  Map<String, String> headers,byte[] body) {
        this.version       = version;
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

    public HttpVersion getVersion() {
        return version;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void send(OutputStream out) throws IOException {
        out.write((version.toString()  + " " + statusCode + " " + statusMessage + CLRF).getBytes(StandardCharsets.US_ASCII));
        for (var kv: headers.entrySet()) {
            String key = Arrays.stream(kv.getKey().split("-"))
                    .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                    .collect(Collectors.joining("-"));
            out.write((key + ": " + kv.getValue()+CLRF).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(CLRF.getBytes(StandardCharsets.US_ASCII));
        out.write(this.body);
    }

    public Object getBody() {
        return this.body;
    }
}
