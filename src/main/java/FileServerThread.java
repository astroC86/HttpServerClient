import data.HttpVerb;
import data.HttpMessage;
import data.HttpMessageParser;
import exceptions.MessageParsingException;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileServerThread extends Thread {

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
                HttpMessage parsedMessage = HttpMessageParser.parse(lines);
                logger.log(Level.INFO, "parsedMessage: {0}", parsedMessage);

                if (parsedMessage.getVerb() == HttpVerb.GET) {
                    File file = new File("./server_content/" +
                            URLDecoder.decode(parsedMessage.getPath(), StandardCharsets.UTF_8));

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
                    out.print("HTTP/" + parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion() + " 200 OK\r\n");
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
}
