package server;

import data.HttpRequest;
import data.parsers.HttpRequestParser;
import data.HttpVerb;
import exceptions.FileCreationException;
import exceptions.FileRetrievalException;
import exceptions.MessageParsingException;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;
import java.util.zip.DataFormatException;
import utils.Content;

import static utils.Content.typeExtension;

public class FileServerThread extends Thread {


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
            HttpRequest parsedMessage = HttpRequestParser.parse(lines);
            logger.log(Level.INFO, "parsedMessage: {0}", parsedMessage);

            // ignore requests that aren't HTTP/1.0 or HTTP/1.1
            if (parsedMessage.getMajorVersion() != 1 || parsedMessage.getMinorVersion() > 1 )
                return false;

            if (parsedMessage.getMinorVersion() == 0) persisting = false;

            if (parsedMessage.getVerb() == HttpVerb.GET) handleGET(out, parsedMessage, binaryOut);
            else handlePOST(in, out, parsedMessage);
        } catch (FileNotFoundException  | FileCreationException          |
                 FileRetrievalException | MissingFormatArgumentException |
                 DataFormatException    | NumberFormatException          |
                 MessageParsingException e) {
            // TODO: why +1 and not +2? CRLF is 2 bytes not 1?!!!
            // TODO: what HTTP version to use in case of error?
            writePrelude(out, "1.0", "text/plain", e.getMessage().getBytes().length + 1, false);
            out.println(e.getMessage());
        }
        return persisting;
    }

    private void handlePOST(InputStream in, PrintWriter out, HttpRequest parsedMessage) throws DataFormatException, IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.getPath(), StandardCharsets.UTF_8));

        if (parsedMessage.lookup("content-length").isEmpty())
            throw new MissingFormatArgumentException("Headers don't include Content-Length.");
        if (parsedMessage.lookup("content-type").isEmpty())
            throw new MissingFormatArgumentException("Headers don't include Content-Type.");

        // the following looks terrible, but it seems to be the safest and most efficient option.
        int contentLength;
        try { contentLength = Integer.parseInt(parsedMessage.lookup("content-length").get()); }
        catch (NumberFormatException e) {
            throw new NumberFormatException("Content-Length is not an integer.");
        }

        String contentType = parsedMessage.lookup("content-type").get();
        if (!typeExtension.containsKey(contentType)) {
            throw new DataFormatException("Acceptable Content-Type's: " +
                    String.join(",", typeExtension.keySet()) + ".");
        }

        String expectedExtension = "."+typeExtension.get(contentType);
        if (!parsedMessage.getPath().endsWith(expectedExtension)) {
            throw new DataFormatException("Content-Type is " + contentType +
                    " , but the extension is not " + expectedExtension + ".");
        }

        try {
            if ((!file.getParentFile().exists() && !file.getParentFile().mkdirs()) ||
                    (!file.exists() && !file.createNewFile())) throw new IOException();
        } catch (IOException e) {
            throw new FileCreationException("Couldn't create " + parsedMessage.getPath());
        }

        try (
                // TODO: do I care enough to handle 5XX type responses? should i respond? should i send 404 anyway?
                FileOutputStream fileStream = new FileOutputStream(file)
        ) {
            fileStream.write(in.readNBytes(contentLength));
        }

        String successMessage = "File " + file.getName() + " was successfully uploaded!";
        writePrelude(out, parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion(), contentType, successMessage.getBytes().length + 1, true);
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

    private void handleGET(PrintWriter out, HttpRequest parsedMessage, OutputStream binaryOut) throws IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.getPath(), StandardCharsets.UTF_8));

        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath() + " doesn't exist.");

        byte[] data = new byte[(int) file.length()];
        int fileLength;
        try (FileInputStream fileStream = new FileInputStream(file)) {
            fileLength = fileStream.read(data);
        } catch (IOException e) {
            throw new FileCreationException(e.getMessage());
        }

        writePrelude(out, parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion(), "text/plain", fileLength, true);
        binaryOut.write(data, 0, fileLength);
    }

    private void writePrelude(PrintWriter out, String version, String contentType, int length, boolean success) {
        if (success)
            out.println("HTTP/" + version + " 200 OK");
        else out.println("HTTP/1.0 404 Not Found");
        out.println("Server: server.FileServer/0.0.1");
        out.println("Date: " + DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        out.println("Content-Type: " + contentType);
        out.println("Content-Length: " + length);
        out.println();
    }
}
