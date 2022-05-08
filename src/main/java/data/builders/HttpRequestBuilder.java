package data.builders;

import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.MIMEType;


import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class HttpRequestBuilder {
    private static final String CLRF = "\r\n";

    private  HttpVerb verb;
    private final  HttpVersion version;
    private URI uri = URI.create("/");
    private HashMap<String,String> headers =  new HashMap<>();
    private byte[] body;
    List<Function> bodyHandlers = new ArrayList<>();

    public HttpRequestBuilder( HttpVerb verb, HttpVersion httpVersion){
        this.version = httpVersion;
        this.verb = verb;
    }

    public HttpRequestBuilder to(URI uri){
        this.uri = uri;
        return this;
    }

    public HttpRequestBuilder withHeader(String head, String value){
        this.headers.put(head.toLowerCase(),value);
        return this;
    }
    public HttpRequestBuilder withBody(String type, byte[] body){
        headers.put("content-type", type);
        headers.put("content-length", String.valueOf(body.length));
        this.body = body;
        return this;
    }

    public HttpRequest build() {
//        if (!headers.containsKey("connection") && version == HttpVersion.HTTP_1_1) {
//            headers.put("connection","keep-alive");
//        }
        var req =  new HttpRequest(uri.toString(),headers,version,verb,body);
        if(bodyHandlers.size() > 0){
            req.addHandlers(bodyHandlers);
        }
        return req;
    }

    public HttpRequestBuilder withBodyHandler(Function fn) {
        bodyHandlers.add(fn);
        return this;
    }
}
