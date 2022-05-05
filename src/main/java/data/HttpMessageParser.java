package data;

import exceptions.MessageParsingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpMessageParser {
    private static final Pattern firstLinePattern = Pattern.compile("^(?<verb>GET|POST)\\s/(?<path>.*) HTTP/(?<version>\\d.\\d)$");
    private static final Pattern headerPattern    = Pattern.compile("^(?<head>[a-zA-Z-_]*): (?<value>(.*))$");

    public static HttpMessage parse(String raw) throws MessageParsingException {
        ArrayList<String> lines  = new ArrayList<>(List.of(raw.split("(?:\r\n|\n)")));
        return parse(lines);
    }

    public static HttpMessage parse(ArrayList<String> lines) throws MessageParsingException {
        if (lines.isEmpty())
            throw new MessageParsingException("Message is empty");

        Matcher matcher = firstLinePattern.matcher(lines.get(0));
        if (!matcher.matches())
            throw new MessageParsingException("First line didn't match the anticipated format.");
        lines.remove(0);
        HttpVerb httpVerb    = HttpVerb.valueOf(matcher.group("verb"));
        String path          = matcher.group("path");
        String httpVersion   = matcher.group("version");
        int httpMajorVersion = Integer.parseInt(httpVersion.split("\\.")[0]);
        int httpMinorVersion = Integer.parseInt(httpVersion.split("\\.")[1]);

        var headers = new HashMap<String,String>();
        for (String line : lines) {
            matcher = headerPattern.matcher(line);
            if (!matcher.matches()) break;
            var head = matcher.group("head");
            var value = matcher.group("value");
            if (headers.containsKey(head)) {
                throw new MessageParsingException("Message contains duplicate headers.");
            } else {
                headers.put(head, value);
            }
        }
        return new HttpMessage(path, headers, httpMajorVersion, httpMinorVersion, httpVerb);
    }
}
