package caching;

import client.SocketFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;

public class LazySocket {
    private final String        host;
    private final int           port;
    private final boolean       persists;
    private final SocketFactory socketFactory;

    private Optional<Socket>    socket = Optional.empty();

    public LazySocket(SocketFactory factory,String host, int port, boolean persists){
        this.host     = host;
        this.port     = port;
        this.persists = persists;
        socketFactory = factory;
    }

    public Optional<Socket> get() throws IOException {
        if (socket.isEmpty()) {
            socket = socketFactory.getOrCreateSocket(host, port, persists);
        }
        return socket;
    }

    public String getSocketAddress() {
        return host+":"+port;
    }
}
