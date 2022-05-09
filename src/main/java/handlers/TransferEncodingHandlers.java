package handlers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransferEncodingHandlers {
    public static HashMap<String,Function> hndlrs =  new HashMap<>();

    public static Function<InputStream,byte[]> chunked =(source) -> {
        byte b; int bytesRemaining = 0;
        var out = new ByteArrayOutputStream();
        boolean firstLine = false;
        try {
            while(true) {
                if(bytesRemaining == 0) {
                    if (firstLine){
                        if (source.read() != '\r') throw new IOException("Malformed chunked encoding");
                        if (source.read() != '\n') throw new IOException("Malformed chunked encoding");
                    }
                    firstLine = true;
                    String hexStr = "";
                    while ((b = (byte) source.read()) != '\r') {
                        hexStr += String.valueOf((char) b);
                    }
                    if (source.read() != '\n') throw new IOException("Malformed chunked encoding");
                    Pattern hexPattern = Pattern.compile("[0-9a-fA-F]+");
                    Matcher m = hexPattern.matcher(hexStr);
                    if(m.find()) {
                        bytesRemaining = Integer.parseInt(m.group(0), 16);
                        if(bytesRemaining == 0)
                            break;
                   }
                    else
                        throw new RuntimeException("Malformed chunk length.");
                }
                bytesRemaining -= 1;
                int temp = source.read();
                out.write(temp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    };

    static {
        hndlrs.put("chunked",chunked);
    }

}
