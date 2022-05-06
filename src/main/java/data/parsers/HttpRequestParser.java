package data.parsers;

import data.HttpRequest;
import data.HttpVerb;
import exceptions.MessageParsingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpRequestParser {
    private static final Pattern firstLinePattern = Pattern.compile("^(?<verb>GET|POST)\\s/(?<path>.*) HTTP/(?<version>\\d.\\d)$");
    private static final Pattern headerPattern    = Pattern.compile("^(?<key>[a-zA-Z-_]*): (?<val>(.*))$");

    public static HttpRequest parse(String raw) throws MessageParsingException {
        ArrayList<String> lines  = new ArrayList<>(List.of(raw.split("(?:\r\n)")));
        return parse(lines);
    }

    public static HttpRequest parse(ArrayList<String> lines) throws MessageParsingException {

        if (lines.isEmpty()) throw new MessageParsingException("Message is empty.");
        Matcher firstLineMatcher = firstLinePattern.matcher(lines.get(0));
        if (!firstLineMatcher.matches())
            throw new MessageParsingException("First line didn't match the anticipated format.");

        HttpVerb httpVerb = HttpVerb.valueOf(firstLineMatcher.group("verb"));
        String path = firstLineMatcher.group("path");
        String httpVersion = firstLineMatcher.group("version");
        int httpMajorVersion = Integer.parseInt(httpVersion.split("\\.")[0]);
        int httpMinorVersion = Integer.parseInt(httpVersion.split("\\.")[1]);

        Map<String, String> headers = new HashMap<>();
        int i;
        for (i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) break;
            Matcher headerMatcher = headerPattern.matcher(line);
            if (!headerMatcher.matches())
                throw new MessageParsingException("Couldn't parse header line: " + line);
            String key = headerMatcher.group("key").toLowerCase();
            String val = headerMatcher.group("val");
            if(headers.containsKey(key))
                throw new MessageParsingException("Request contains duplicate headers.");
            else if (val.isEmpty() || val.isBlank())
                throw new MessageParsingException(String.format("Value is empty for %s",key));
            else
                headers.put(key, val);
        }
        return new HttpRequest(path, headers, httpMajorVersion, httpMinorVersion, httpVerb);
    }
}
