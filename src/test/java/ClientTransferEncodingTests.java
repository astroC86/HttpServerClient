import client.HttpClientThread;
import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import handlers.BodyHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

public class ClientTransferEncodingTests {
    private OutputStream output;

    @BeforeEach
    void initialize_output_stream(){
        output = new OutputStream() {
            private final StringBuilder string = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b );
            }
            @Override
            public void write(byte[] bytes){
                for (var b:bytes) {
                    this.string.append((char) b );
                }
            }
            public String toString() {
                return this.string.toString();
            }
        };
    }

    @Test
    public void should_processes_chuncked(){
        var out = new ByteArrayOutputStream();
        var q = new ArrayDeque<Pair<HttpRequest, Integer>>();
        var mck = new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                .to(URI.create("/Chunked"))
                .withHeader("Host","anglesharp.azurewebsites.net")
                .withHeader("User-Agent","Wget/1.19.4 (linux-gnu)")
                .withHeader("Accept","*/*")
                .withHeader("Accept-Encoding","identity")
                .withHeader("Connection","Keep-Alive")
                .withBodyHandler(BodyHandlers.ofStream.apply(out))
                .build();
        q.add(new Pair<>(mck,80));
        var client =  new HttpClientThread(q);
        String exp  = """
                <!DOCTYPE html>\r
                <html lang=en>\r
                <head>\r
                <meta charset='utf-8'>\r
                <title>Chunked transfer encoding test</title>\r
                </head>\r
                <body><h1>Chunked transfer encoding test</h1><h5>This is a chunked response after 100 ms.</h5><h5>This is a chunked response after 1 second. The server should not close the stream before all chunks are sent to a client.</h5></body></html>""";
        var res = out.toString();
        assertEquals(res,exp);
    }
}
