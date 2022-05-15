package server;

import collections.Pair;
import data.*;
import data.builders.HttpResponseBuilder;
import data.parsers.HttpRequestParser;
import exceptions.FileCreationException;
import exceptions.FileRetrievalException;
import exceptions.MessageParsingException;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Supplier;
import java.util.logging.*;
import java.util.zip.DataFormatException;

import static utils.Content.readLine;
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
    private final Runnable callback;
    private Supplier<Integer> timeoutSupplier;
    private PriorityBlockingQueue<Pair<HttpResponse, Integer>> queue = new PriorityBlockingQueue<>(1, Comparator.comparingInt(o -> o._1));

    FileServerThread(Socket clientSocket, String threadName, Runnable callback, Supplier<Integer> timeoutSupplier) {
        this.clientSocket = clientSocket;
        this.callback = callback;
        this.timeoutSupplier = timeoutSupplier;
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
        try {
            InputStream in = clientSocket.getInputStream();
            OutputStream binaryOut = clientSocket.getOutputStream();

            Thread queueThread = new Thread(() -> {
                int currentResponseNo = 0;
                while (true) {
                    if (queue.isEmpty() || (queue.peek()._1 != currentResponseNo && queue.peek()._1 != Integer.MAX_VALUE)) continue;
                    currentResponseNo++;
                    Pair<HttpResponse, Integer> responsePair = queue.poll();
                    if (responsePair != null) {
                        try {
                            HttpResponse response = responsePair._0;
                            if (response == null) {
                                clientSocket.close();
                                return;
                            }
                            response.send(clientSocket.getOutputStream());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            queueThread.start();

            int responseNo = 0;
            boolean persisting = true;
            while (persisting) {
                ArrayList<String> lines = new ArrayList<>();
                while (true) {
                    clientSocket.setSoTimeout(timeoutSupplier.get()); // SocketTimeoutException <: IOException
                    String line = readLine(in);
                    if (line == null) return; // end of stream
                    if (line.isEmpty()) break; //RFC 2616 dictates that nothing precedes CRLF
                    lines.add(line);
                }

                logger.log(Level.INFO, "input:\n  {0}", String.join("\n  ", lines));

                persisting = respond(in, binaryOut, lines, responseNo++);
            }
        } catch (SocketTimeoutException e) {
            logger.log(Level.INFO, e.getMessage());
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }

        queue.add(new Pair<>(null, Integer.MAX_VALUE));
        callback.run();
    }

    private boolean respond(InputStream in, OutputStream binaryOut, ArrayList<String> lines, int responseNo) throws IOException {
        boolean persisting = true;
        try {
            HttpRequest parsedMessage = HttpRequestParser.parse(lines);
            logger.log(Level.INFO, "parsedMessage: {0}", parsedMessage);

            // ignore requests that aren't HTTP/1.0 or HTTP/1.1
            if (parsedMessage.getMajorVersion() != 1 || parsedMessage.getMinorVersion() > 1)
                return false;

            HttpVersion version = switch (parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion()) {
                case "1.1" -> HttpVersion.HTTP_1_1;
                case "1.0" -> HttpVersion.HTTP_1_0;
                default -> throw new RuntimeException();
            };

            persisting = switch (version) {
                case HTTP_1_0 -> false;
                case HTTP_1_1 -> true;
            };

            var connectionOption =  parsedMessage.lookup("connection").map(String::toLowerCase);
            if (connectionOption.isPresent()) {
                persisting = switch (connectionOption.get()) {
                    case "keep-alive" -> true;
                    case "close" -> false;
                    default -> throw new DataFormatException("Header connection can either be keep-alive or close.");
                };
            }

            if (parsedMessage.getVerb() == HttpVerb.GET) {
                if(persisting){
                    new Thread(() -> {
                        try {
                            handleGET(parsedMessage, binaryOut, responseNo);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else handleGET(parsedMessage, binaryOut, responseNo);
            } else
                handlePOST(in, binaryOut, parsedMessage, responseNo);

        } catch (FileNotFoundException | FileCreationException |
                 FileRetrievalException | MissingFormatArgumentException |
                 DataFormatException | NumberFormatException |
                 MessageParsingException e) {
            int statusCode;
            if (e instanceof FileNotFoundException) statusCode = 404;
            else if (e instanceof FileCreationException || e instanceof FileRetrievalException) statusCode = 500;
            else statusCode = 400;
            var response = preparePrelude(HttpVersion.HTTP_1_1)
                    .withBody(MIMEType.PLAINTEXT, (e.getMessage() + "\r\n").getBytes())
                    .withResponseCode(statusCode)
                    .build();
            queue.add(new Pair<>(response, responseNo));
        }
        return persisting;
    }

    private void handlePOST(InputStream in, OutputStream binaryOut, HttpRequest parsedMessage, int responseNo) throws DataFormatException, IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.getPath(), StandardCharsets.UTF_8));

        Optional<String> contentLengthOptional = parsedMessage.lookup("content-length");
        Optional<String> contentTypeOptional = parsedMessage.lookup("content-type");
        if (contentLengthOptional.isEmpty())
            throw new MissingFormatArgumentException("Headers don't include Content-Length.");
        if (contentTypeOptional.isEmpty())
            throw new MissingFormatArgumentException("Headers don't include Content-Type.");

        // the following looks terrible, but it seems to be the safest and most efficient option.
        int contentLength;
        try {
            contentLength = Integer.parseInt(contentLengthOptional.get());
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Content-Length is not an integer.");
        }

        String contentType = contentTypeOptional.get();
        if (!typeExtension.containsKey(contentType)) {
            throw new DataFormatException("Acceptable Content-Type's: " +
                    String.join(",", typeExtension.keySet()) + ".");
        }

        String expectedExtension = "." + typeExtension.get(contentType);
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
                FileOutputStream fileStream = new FileOutputStream(file)
        ) {
            fileStream.write(in.readNBytes(contentLength));
        } catch (IOException e) {
            // TODO: create a new exception class?
            throw new FileCreationException("Error writing to " + parsedMessage.getPath());
        }

        String successMessage = "File " + file.getName() + " was successfully uploaded!";
        HttpVersion version = switch (parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion()) {
            case "1.1" -> HttpVersion.HTTP_1_1;
            case "1.0" -> HttpVersion.HTTP_1_0;
            default -> throw new RuntimeException();
        };
        var response = preparePrelude(version)
                .withResponseCode(200)
                .withBody(MIMEType.PLAINTEXT, (successMessage + "\r\n").getBytes())
                .build();

        queue.add(new Pair(response, responseNo));
    }

    private void handleGET(HttpRequest parsedMessage, OutputStream binaryOut, int responseNo) throws IOException {
        File file = new File("./server_content/" +
                URLDecoder.decode(parsedMessage.getPath(), StandardCharsets.UTF_8));

        if (!file.exists()) throw new FileNotFoundException(parsedMessage.getPath() + " doesn't exist.");

        byte[] data;
        try (FileInputStream fileStream = new FileInputStream(file)) {
            data = fileStream.readAllBytes();
        } catch (IOException | AssertionError e) {
            throw new FileRetrievalException("Couldn't read " + parsedMessage.getPath());
        }

        HttpVersion version = switch (parsedMessage.getMajorVersion() + "." + parsedMessage.getMinorVersion()) {
            case "1.1" -> HttpVersion.HTTP_1_1;
            case "1.0" -> HttpVersion.HTTP_1_0;
            default -> throw new RuntimeException();
        };

        String[] splits = file.getName().split("\\.");
        MIMEType type = !file.getName().contains(".") ? MIMEType.PLAINTEXT : switch (splits[splits.length - 1]) {
            case "png" -> MIMEType.PNG;
            case "txt" -> MIMEType.PLAINTEXT;
            case "html" -> MIMEType.HTML;
            default -> MIMEType.BLOB;
        };

        HttpResponse response = preparePrelude(version)
                .withResponseCode(200)
                .withBody(type, data)
                .build();

        queue.add(new Pair(response, responseNo));
    }

    private HttpResponseBuilder preparePrelude(HttpVersion version) {
        return new HttpResponseBuilder(version)
                .withHeader("Server", "FileServer/0.0.1")
                .withHeader("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
    }
}
