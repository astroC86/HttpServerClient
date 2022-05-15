package caching;

import client.SocketFactory;
import data.HttpRequest;
import data.HttpResponse;
import exceptions.MessageParsingException;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpResponseExecutor {
    private ClientCache   cache;
    private SocketFactory socketFactory;
    private Logger logger;

    public HttpResponseExecutor(Logger lgr){
        logger        = lgr;
        socketFactory = new SocketFactory(lgr);
        cache         = new ClientCache(lgr);
    }

    public Optional<HttpResponse> execute(String host, int port, HttpRequest request) throws MessageParsingException, IOException {
        var lazySocket =  new LazySocket(socketFactory,host,port, request.persists());
        Optional<HttpResponse> responseOptional  = cache.process(request,lazySocket);
        if (responseOptional.isPresent()) {
            var response = responseOptional.get();
            var socketOptional = lazySocket.get();
            if (!response.persists() || (socketOptional.isEmpty() && !request.persists())) {
                socketFactory.remove(host,port);
                if (socketOptional.isPresent()) {
                   var socket = socketOptional.get();
                   socket.close();
                }
            }
        }
        return responseOptional;
    }
    public void dispose(){
        try {
            socketFactory.dispose();
        } catch (IOException e) {
            logger.log(Level.SEVERE,"Failed to dispose of open persistent sockets.");
        }
    }
}
