package client;

import data.HttpVerb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpClient {
    public static void main(String[] args) throws IOException {
        int port                  = Integer.parseInt(args[0]);
        String commandsFile       = args[1];
        Path fileName = Path.of(commandsFile);
        ServerSocket clientSocket = new ServerSocket(port);
        var commands = Files.readString(fileName).split("\n");
        for (String command : commands) {
            System.out.println(command);
        }
    }
}
