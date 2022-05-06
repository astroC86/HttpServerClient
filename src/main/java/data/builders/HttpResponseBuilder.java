package data.builders;

import data.HttpResponse;
import data.HttpVersion;
import data.MIMEType;

import java.util.HashMap;

public class HttpResponseBuilder {
    private final String CLRF = "\r\n";
    private final HttpVersion version;

    private HashMap<String, String> headers = new HashMap<>();
    private int statusCode;
    private String statusMessage;
    private byte[] body;

    public HttpResponseBuilder(HttpVersion version) {
        this.version = version;
    }


    public HttpResponseBuilder withResponseCode(int statusCode) {
        this.statusCode = statusCode;
        this.statusMessage = switch (statusCode) {
            case 200 -> "OK";
            case 404 ->  "Not Found";
            case 500 -> "Internal Server Error";
            case 400 -> "Bad Request";
            default -> throw new IllegalArgumentException("Unexpected statusCode: " + statusCode);
        };
        return this;
    }


    public HttpResponseBuilder withHeader(String head, String value) {
        this.headers.put(head.toLowerCase(), value);
        return this;
    }

    public HttpResponseBuilder withBody(String type, byte[] body) {
        headers.put("content-type", type);
        headers.put("content-length", String.valueOf(body.length));
        this.body = body;
        return this;
    }

    public HttpResponseBuilder withBody(MIMEType type, byte[] body) {
        return withBody(type.toString(), body);
    }

    public HttpResponse build() {
        return new HttpResponse(version, statusCode, statusMessage, body, headers);
    }
}
