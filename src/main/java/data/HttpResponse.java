package data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class HttpResponse {
    private final String CLRF = "\r\n";
    HttpVersion version;

    private HashMap<String,String> headers =  new HashMap<>();
    private int statusCode;
    private String statusMessage;
    private byte[] body;

    public HttpResponse(int major,int minor, int statusCode, String statusMessage, byte[] body) {
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
    }

    public HttpResponse(HttpVersion version, int statusCode, String statusMessage, byte[] body) {
        this.version       = version;
        this.statusCode    = statusCode;
        this.statusMessage = statusMessage;
        this.body = body;
    }

    public void send(OutputStream out) throws IOException {
        out.write(("HTTP/"+ version.toString()+CLRF).getBytes(StandardCharsets.US_ASCII));
        for (var kv: headers.entrySet()) {
            out.write((kv.getKey()+": "+kv.getValue()+CLRF).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(CLRF.getBytes(StandardCharsets.US_ASCII));
        out.write(this.body);
    }

    public Object getBody() {
        return this.body;
    }
}
