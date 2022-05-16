package client;

import caching.HttpResponseExecutor;
import collections.Pair;
import data.HttpRequest;
import exceptions.MessageParsingException;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HttpClientThread {
    private final Logger  logger;
    private final HttpResponseExecutor executor;


    public HttpClientThread(Queue<Pair<HttpRequest,Integer>> requests)  {
        this.logger       = Logger.getLogger("Client Thread");
        this.logger.setLevel(Level.ALL);

        this.executor    = new HttpResponseExecutor(logger);

        while (!requests.isEmpty()){
            var top = requests.poll();
            var req = top._0;
            logger.log(Level.INFO, "<Sending>:\n"+req);
            var hostOptional = req.lookup("Host");
            if (hostOptional.isEmpty()){
                logger.log(Level.SEVERE, "Invalid Request, Host not defined.");
                continue;
            }
            var host = hostOptional.get();
            var port = top._1;
            try {
                executor.execute(host,port,req);
            } catch (MessageParsingException e) {
                logger.log(Level.SEVERE, String.format("<Parse Error> Failed to parse response for request:\n%s",req));
            }
        }
        this.executor.dispose();
    }
}
