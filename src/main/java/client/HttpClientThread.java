package client;

import data.HttpRequest;
import data.HttpResponse;
import data.parsers.HttpResponseParser;
import exceptions.MessageParsingException;

import java.io.*;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpClientThread extends Thread{
    private Logger         logger;
    private OutputStream   request;
    private BufferedReader response;
    private Socket         clientSocket;
    private HttpRequest    httpRequest;

    /**
     * Constructor method for the Http Client Server.
     * @param clientSocket The socket that the client is going to use to communicate on
     * @param threadName   The name of the client thread
     * @param request
     */
    public HttpClientThread(Socket clientSocket, String threadName, HttpRequest request) throws IOException {
        this.request      = clientSocket.getOutputStream();
        this.response     = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.clientSocket = clientSocket;
        this.httpRequest  = request;
        this.logger       = Logger.getLogger(threadName);
        this.logger.setLevel(Level.ALL);
        this.setName(threadName);
    }

    @Override
    public void run() {
        try {
            this.httpRequest.send(this.request);

            String responseLine;
            while ((responseLine = response.readLine()) != null) {
                System.out.println(responseLine);
            }
            HttpResponse httpResponse = HttpResponseParser.parse("");
            for (var bh: this.httpRequest.getBodyHandlers()) {
                bh.apply(httpResponse.getBody());
            }

            response.close();
            request.close();
            clientSocket.close();
        } catch (IOException | MessageParsingException e) {
            e.printStackTrace();
        }
    }
}
