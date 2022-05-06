package data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpResponse {
    private final String CLRF = "\r\n";
    HttpVersion version;

    private Map<String,String> headers;
    private int statusCode;
    private String statusMessage;
    private byte[] body;

    public HttpResponse(int major,int minor, int statusCode, String statusMessage, byte[] body, Map<String, String> headers) {
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
        this.body = body;
        this.headers = headers;
    }

    public HttpResponse(HttpVersion version, int statusCode, String statusMessage, byte[] body, Map<String, String> headers) {
        this.version       = version;
        this.statusCode    = statusCode;
        this.statusMessage = statusMessage;
        this.body = body;
        this.headers = headers;
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
