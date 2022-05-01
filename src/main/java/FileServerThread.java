import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.rmi.UnexpectedException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final Socket clientSocket;
    private final Logger logger;

    FileServerThread(Socket clientSocket, String threadName) {
        this.clientSocket = clientSocket;
        this.logger = Logger.getLogger(threadName);
        this.logger.setLevel(Level.ALL);
        this.setName(threadName);
    }

    // TODO: imitate HTTP spec.
    // TODO: decide if we care enough about \r\n (should we not accept \n only?)
    @Override
    public void run() {
        try (
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {
            ArrayList<String> lines = new ArrayList<>();
            String line;
            int count = 4;
            while ((line = in.readLine()) != null && count-- > 0) {
                lines.add(line);
                logger.log(Level.INFO, "input: {0}", line);
            }

            try {
                ParsedMessage parsedMessage = parseMessage(lines);
                logger.log(Level.INFO, "parsedMessage: {0}", parsedMessage);

                if (parsedMessage.verb == HTTPVerb.GET) {
                    File file = new File("./server_content/" +
                            URLDecoder.decode(parsedMessage.path, StandardCharsets.UTF_8));

                    if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath() + " doesn't exist.");

                    byte[] data = new byte[(int) file.length()];
                    int fileLength;
                    try (FileInputStream fileStream = new FileInputStream(file)) {
                        fileLength = fileStream.read(data);
                    } catch (IOException e) {
                        // TODO: there is probably a better exception class for this that doesn't class with IOException
                        throw new UnexpectedException(e.getMessage());
                    }

                    // TODO: refactor this into a separate method
                    ZonedDateTime time = LocalDateTime.now().atZone(ZoneId.of("GMT")); // HTTP spec states this must be in GMT
                    out.print("HTTP/" + parsedMessage.majorVersion + "." + parsedMessage.minorVersion + " 200 OK\r\n");
                    out.print("Server: FileServer/0.0.1\r\n");
                    out.print("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(time) + "\r\n");
                    out.println("Content-Type: text/plain\r\n");
                    out.print("Content-Length: " + fileLength + "\r\n");
                    out.print("\r\n");
                    clientSocket.getOutputStream().write(data, 0, fileLength);
                }
            } catch (MessageParsingException | FileNotFoundException | UnexpectedException e) {
                ZonedDateTime time = LocalDateTime.now().atZone(ZoneId.of("GMT")); // HTTP spec states this must be in GMT
                // TODO: what HTTP version to use?
                out.print("HTTP/1.0 404 Not Found\r\n");
                out.print("Server: FileServer/0.0.1\r\n");
                out.print("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(time) + "\r\n");
                out.println("Content-Type: text/plain\r\n");
                out.print("Content-Length: " + e.getMessage().getBytes().length + "\r\n");
                out.print("\r\n");
                out.print(e.getMessage());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    // TODO: headers and the rest
    private ParsedMessage parseMessage(ArrayList<String> lines) throws MessageParsingException {
        final Pattern firstLinePattern = Pattern.compile("^(?<verb>GET|POST)\\s/(?<path>.*) HTTP/(?<version>\\d.\\d)$");
        final Pattern headerPattern = Pattern.compile("^([a-zA-Z-_]*): (.*)$");

        if (lines.isEmpty()) throw new MessageParsingException("Message is empty");
        Matcher matcher = firstLinePattern.matcher(lines.get(0));
        if (!matcher.matches())
            throw new MessageParsingException("First line didn't match the anticipated format.");

        HTTPVerb httpVerb = HTTPVerb.valueOf(matcher.group("verb"));
        String path = matcher.group("path");
        String httpVersion = matcher.group("version");
        int httpMajorVersion = Integer.parseInt(httpVersion.split("\\.")[0]);
        int httpMinorVersion = Integer.parseInt(httpVersion.split("\\.")[1]);

        return new ParsedMessage(path, null, httpMajorVersion, httpMinorVersion, httpVerb);
    }
}
