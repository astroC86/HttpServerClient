import data.HttpMessage;
import data.HttpMessageParser;
import data.HttpVerb;
import exceptions.MessageParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class HttpParserTests {
    private static class MockRequest{
        HashMap<String,String> header =  new HashMap<>();
        public final HttpVerb verb;
        public final int minor;
        public final int major;
        public final String CLRF = "\r\n";

        private MockRequest(HttpVerb verb, int minor, int major) {
            this.verb  = verb;
            this.minor = minor;
            this.major = major;
        }

        public MockRequest withHeader(String head, String value){
            this.header.put(head,value);
            return this;
        }
        public String build(){
            String res  = this.verb.name()+" / HTTP/"+ minor +"."+ major+CLRF;
            for (var kv: header.entrySet()) {
                res += kv.getKey()+": "+kv.getValue()+CLRF;
            }
            return res;
        }
    }
    @Test
    void should_parse_headers_properly() {
        MockRequest mck = new MockRequest(HttpVerb.GET, 1, 1)
                            .withHeader("User-Agent","Wget/1.19.4 (linux-gnu)")
                            .withHeader("Accept","*/*")
                            .withHeader("Accept-Encoding","identity")
                            .withHeader("Host","localhost:8000")
                            .withHeader("Connection","Keep-Alive");

        String msg = mck.build();
        try {
            HttpMessage res = HttpMessageParser.parse(msg);
            for(var kv: mck.header.entrySet()) {
                var head = res.lookup(kv.getKey());
                assertFalse(head.isEmpty(), "User Agent is not parsed");
                assertEquals(head.get(),kv.getValue(),"value is corrupt for head: "+kv.getKey());
            }
        }catch (MessageParsingException exception){
            fail();
        }
    }

    @Test
    void message_parse_without_header(){
        MockRequest mck = new MockRequest(HttpVerb.GET, 1, 1);
        String msg = mck.build();
        try {
            HttpMessage res = HttpMessageParser.parse(msg);
            assertEquals(res.headerCount(),0);
        }catch (MessageParsingException exception){
            fail();
        }
    }
}
