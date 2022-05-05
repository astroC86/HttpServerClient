import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

public class FileServerThread extends Thread {
    private enum HTTPVerb {GET, POST}

    private static class ParsedMessage {
        String path;
        Map<String, String> headers;
        int majorVersion;
        int minorVersion;
        HTTPVerb verb;

        public ParsedMessage(String path, Map<String, String> headers, int majorVersion, int minorVersion, HTTPVerb verb) {
            this.path = path;
            this.headers = headers;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.verb = verb;
        }

        @Override
        public String toString() {
            return "ParsedMessage{" +
                    "path='" + path + '\'' +
                    ", headers=" + headers +
                    ", majorVersion=" + majorVersion +
                    ", minorVersion=" + minorVersion +
                    ", verb=" + verb +
                    '}';
        }
    }

    private static class MessageParsingException extends Exception {
        public MessageParsingException(String message) {
            super(message);
        }
    }

    private static class FileRetrievalException extends IOException {
        public FileRetrievalException(String message) {
            super(message);
        }
    }

    private static class FileCreationException extends IOException {
        public FileCreationException(String message) {
            super(message);
        }
    }

    private static class SimpleFormatterWithThreadName extends SimpleFormatter {
        final String threadName;
        public SimpleFormatterWithThreadName(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public String format(LogRecord rec) {
            return threadName + " " + super.format(rec);
        }
    }

    private final Socket clientSocket;
    private final Logger logger;

    FileServerThread(Socket clientSocket, String threadName) {
        this.clientSocket = clientSocket;
        this.setName(threadName);

        this.logger = Logger.getLogger(threadName);
        this.logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatterWithThreadName(threadName));
        this.logger.addHandler(handler);
    }

    @Override
    public void run() {
        try (
                InputStream in = clientSocket.getInputStream();
                OutputStream binaryOut = clientSocket.getOutputStream();
                PrintWriter out = new PrintWriter(binaryOut, true)
        ) {
            boolean persisting = true;
            while (persisting) {
                ArrayList<String> lines = new ArrayList<>();
                while (true) {
                    String line = readLine(in);
                    if (line == null) return; // end of stream
                    if (line.isEmpty()) break; //RFC 2616 dictates that nothing precedes CRLF
                    lines.add(line);
                }

                logger.log(Level.INFO, "input:\n  {0}", String.join("\n  ", lines));

                persisting = respond(in, binaryOut, out, lines);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    private boolean respond(InputStream in, OutputStream binaryOut, PrintWriter out, ArrayList<String> lines) throws IOException {
        boolean persisting = true;
        try {
            ParsedMessage parsedMessage = parseMessage(lines);
            logger.log(Level.INFO, "parsedMessage: {0}", parsedMessage);

            // ignore requests that aren't HTTP/1.0 or HTTP/1.1
            if (parsedMessage.majorVersion != 1 || parsedMessage.minorVersion > 1 )
                return false;

            if (parsedMessage.minorVersion == 0) persisting = false;

            if (parsedMessage.verb == HTTPVerb.GET) handleGET(out, parsedMessage, binaryOut);
            else handlePOST(in, out, parsedMessage);
        } catch (MessageParsingException | FileNotFoundException | FileCreationException | FileRetrievalException |
                 MissingFormatArgumentException | DataFormatException | NumberFormatException e) {
            // TODO: why +1 and not +2? CRLF is 2 bytes not 1?!!!
            // TODO: what HTTP version to use in case of error?
            writePrelude(out, "1.0", "text/plain", e.getMessage().getBytes().length + 1, false);
            out.println(e.getMessage());
        }
        return persisting;
    }

    private void handlePOST(InputStream in, PrintWriter out, ParsedMessage parsedMessage) throws DataFormatException, IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.path, StandardCharsets.UTF_8));

        Map<String, String> headers = parsedMessage.headers;
        if (!headers.containsKey("content-length"))
            throw new MissingFormatArgumentException("Headers don't include Content-Length.");
        if (!headers.containsKey("content-type"))
            throw new MissingFormatArgumentException("Headers don't include Content-Type.");

        // the following looks terrible, but it seems to be the safest and most efficient option.
        int contentLength;
        try { contentLength = Integer.parseInt(headers.get("content-length")); }
        catch (NumberFormatException e) {
            throw new NumberFormatException("Content-Length is not an integer.");
        }

        Map<String, String> contentTypeToExtension = new HashMap<>();
        contentTypeToExtension.put("image/png", ".png");
        contentTypeToExtension.put("text/html", ".html");
        contentTypeToExtension.put("text/plain", ".txt");

        String contentType = headers.get("content-type");
        if (!contentTypeToExtension.containsKey(contentType)) {
            throw new DataFormatException("Acceptable Content-Type's: " +
                    String.join(",", contentTypeToExtension.keySet()) + ".");
        }

        String expecetedExtension = contentTypeToExtension.get(contentType);
        if (!parsedMessage.path.endsWith(expecetedExtension)) {
            throw new DataFormatException("Content-Type is " + contentType +
                    " , but the extension is not " + expecetedExtension + ".");
        }

        try {
            if (!file.getParentFile().mkdirs() || file.createNewFile()) throw new IOException();
        } catch (IOException e) {
            throw new FileCreationException("Couldn't create " + parsedMessage.path);
        }

        try (
                // TODO: do I care enough to handle 5XX type responses? should i respond? should i send 404 anyway?
                FileOutputStream fileStream = new FileOutputStream(file)
        ) {
            fileStream.write(in.readNBytes(contentLength));
        }

        String successMessage = "File " + file.getName() + " was successfully uploaded!";
        writePrelude(out, parsedMessage.majorVersion + "." + parsedMessage.minorVersion, contentType, successMessage.getBytes().length + 1, true);
        out.println(successMessage);
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

    private void handleGET(PrintWriter out, ParsedMessage parsedMessage, OutputStream binaryOut) throws IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.path, StandardCharsets.UTF_8));

        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath() + " doesn't exist.");

        byte[] data = new byte[(int) file.length()];
        int fileLength;
        try (FileInputStream fileStream = new FileInputStream(file)) {
            fileLength = fileStream.read(data);
        } catch (IOException e) {
            throw new FileCreationException(e.getMessage());
        }

        writePrelude(out, parsedMessage.majorVersion + "." + parsedMessage.minorVersion, "text/plain", fileLength, true);
        binaryOut.write(data, 0, fileLength);
    }

    private void writePrelude(PrintWriter out, String version, String contentType, int length, boolean success) {
        if (success)
            out.println("HTTP/" + version + " 200 OK");
        else out.println("HTTP/1.0 404 Not Found");
        out.println("Server: FileServer/0.0.1");
        out.println("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        out.println("Content-Type: " + contentType);
        out.println("Content-Length: " + length);
        out.println();
    }

    private ParsedMessage parseMessage(ArrayList<String> lines) throws MessageParsingException {
        final Pattern firstLinePattern = Pattern.compile("^(?<verb>GET|POST)\\s/(?<path>.*) HTTP/(?<version>\\d.\\d)$");

        if (lines.isEmpty()) throw new MessageParsingException("Message is empty.");
        Matcher firstLineMatcher = firstLinePattern.matcher(lines.get(0));
        if (!firstLineMatcher.matches())
            throw new MessageParsingException("First line didn't match the anticipated format.");

        HTTPVerb httpVerb = HTTPVerb.valueOf(firstLineMatcher.group("verb"));
        String path = firstLineMatcher.group("path");
        String httpVersion = firstLineMatcher.group("version");
        int httpMajorVersion = Integer.parseInt(httpVersion.split("\\.")[0]);
        int httpMinorVersion = Integer.parseInt(httpVersion.split("\\.")[1]);

        final Pattern headerPattern = Pattern.compile("^(?<key>[a-zA-Z-_]*): (?<val>.*)$");

        Map<String, String> headers = new HashMap<>();
        int i;
        for (i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) break;
            Matcher headerMatcher = headerPattern.matcher(line);
            if (!headerMatcher.matches()) throw new MessageParsingException("Couldn't parse header line: " + line);
            String key = headerMatcher.group("key").toLowerCase();
            String val = headerMatcher.group("val");
            headers.put(key, val);
        }

        return new ParsedMessage(path, headers, httpMajorVersion, httpMinorVersion, httpVerb);
    }
}
