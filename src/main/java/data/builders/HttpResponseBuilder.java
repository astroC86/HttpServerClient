package data.builders;

import data.HttpResponse;
import data.HttpVersion;

import java.util.HashMap;

public class HttpResponseBuilder {
    private final String CLRF = "\r\n";
    private final HttpVersion version;

    private HashMap<String,String> headers =  new HashMap<>();
    private int statusCode;
    private String statusMessage;
    private byte[] body;

    public HttpResponseBuilder(HttpVersion version) {
        this.version = version;
    }


    public HttpResponseBuilder withResponseCode(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        return this;
    }


    public HttpResponseBuilder withHeader(String head, String value){
        this.headers.put(head.toLowerCase(),value);
        return this;
    }


    public HttpResponseBuilder withBody(String type,byte[] body){
        headers.put("content-type",type);
        headers.put("content-length", String.valueOf(body.length));
        this.body = body;
        return this;
    }

    public HttpResponse build(){
        return new HttpResponse(version,statusCode,statusMessage,body);
    }
}
