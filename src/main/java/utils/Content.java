package utils;

import collections.BiMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Content {
    public static final BiMap<String, String> typeExtension = new BiMap<>();
    static {
        typeExtension.put("image/png", "png");
        typeExtension.put("text/html", "html");
        typeExtension.put("text/plain", "txt");
    }

    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int c;
        boolean lastCharIsReturn = false;
        for (c = in.read(); !(c == '\n' && lastCharIsReturn) && c != -1; c = in.read()) {
            lastCharIsReturn = c == '\r';
            if (!lastCharIsReturn) byteArrayOutputStream.write(c);
        }

        if (c == -1 && byteArrayOutputStream.size() == 0) { //end of stream
            return null;
        }
        return byteArrayOutputStream.toString();
    }
}
