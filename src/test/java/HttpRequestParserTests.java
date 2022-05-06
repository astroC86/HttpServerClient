import data.HttpRequest;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import data.parsers.HttpRequestParser;
import data.HttpVerb;
import exceptions.MessageParsingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class HttpRequestParserTests {
    private static Field headerField;
    private OutputStream output;

    @BeforeAll
    static void setup(){
        try {
            headerField = HttpRequestBuilder.class.getDeclaredField("headers");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        headerField.setAccessible(true);
    }

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
    void should_parse_headers_properly() {
        var mck = new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                                                    .withHeader("User-Agent","Wget/1.19.4 (linux-gnu)")
                                                    .withHeader("Accept","*/*")
                                                    .withHeader("Accept-Encoding","identity")
                                                    .withHeader("Host","localhost:8000")
                                                    .withHeader("Connection","Keep-Alive").build();
        try {
            mck.send(output);
            String msg = output.toString();
            HashMap<String,String> header = (HashMap<String,String>)headerField.get(mck);
            HttpRequest res = HttpRequestParser.parse(msg);
            for(var kv: header.entrySet()) {
                var head = res.lookup(kv.getKey());
                assertFalse(head.isEmpty(), "User Agent is not parsed");
                assertEquals(head.get(),kv.getValue(),"value is corrupt for head: "+kv.getKey());
            }
        }catch (MessageParsingException exception){
            fail();
        } catch (IllegalAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void message_parse_without_header() {
        var mck = new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1).build();
        try {
            mck.send(output);
            String msg = output.toString();
            HttpRequest res = HttpRequestParser.parse(msg);
            assertEquals(res.headerCount(),0);
        } catch (MessageParsingException e) {
            fail();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void should_produce_exception_on_duplicate_headers() {
        var mck = new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                                            .withHeader("User-Agent","Wget/1.19.4 (linux-gnu)")
                                            .withHeader("Accept","*/*").build();
        try {
            mck.send(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String msg = output.toString();
        msg =   msg.substring(0,msg.length()-2)+ "accept: */*\r\n";
        String finalMsg = msg;
        Exception exception = assertThrows(MessageParsingException.class, ()-> {
            HttpRequest res = HttpRequestParser.parse(finalMsg);
        });
    }

    @Test
    void should_produce_exception_on_empty_header() {
        var mck = new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                                            .withHeader("User-Agent","Wget/1.19.4 (linux-gnu)")
                                            .withHeader("Accept","").build();
        try {
            mck.send(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String msg = output.toString();
        Exception exception = assertThrows(MessageParsingException.class, () -> {
            HttpRequest res = HttpRequestParser.parse(msg);
        });
    }


}
