import handlers.TransferEncodingHandlers;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

public class TransferEncodingsTests {
    @Test
    public void should_parse_chunked_transfer_coding(){
        String req = """
                        7\r
                        Mozilla\r
                        9\r
                        Developer\r
                        7\r
                        Network\r
                        0\r
                        \r\n""";

        InputStream transferStream = new ByteArrayInputStream(req.getBytes());
        var bytes  = TransferEncodingHandlers.chunked.apply(transferStream);
        assertEquals(bytes.length,23);
        var parsed = new String(bytes);
        assertEquals(parsed,"MozillaDeveloperNetwork");
    }
}
