package client;

import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import handlers.BodyGenerators;
import handlers.BodyHandlers;
import utils.FileUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static utils.Content.typeExtension;


public class HttpClient {
    private static final Pattern linePattern = Pattern.compile(
                                                    "^(?<verb>POST|GET)" +
                                                            "(?:\\s+)" +
                                                            "(?<fname>(?:(?:/)|(?:.*?\\.(?<ext>[^.]\\w+))))" +
                                                            "(?:\\s+)" +
                                                            "(?<hostname>(?:(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|" +
                                                                        "(?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)+(?:[A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])))" +
                                                            "(?:\\s+)?(?<port>\\d+)?$");

    public static void main(String[] args) {
        String      commandsFile   = args[0];
        Path        fileName       = Path.of(commandsFile);
        Queue<Pair<HttpRequest,Integer>> requestsq = new ArrayDeque<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fileName.toFile()))) {
            String line; int lineCount=0;
            while ((line = br.readLine()) != null && (++lineCount > 0) ){
                var matcher  = linePattern.matcher(line);
                if (matcher.matches()) {
                    var verb  = HttpVerb.valueOf(matcher.group("verb"));
                    var fname = matcher.group("fname");
                    var ext   = matcher.group("ext");
                    if (ext != null){
                        if (!typeExtension.inverse().containsKey(ext)) {
                            System.err.println("Extension(" + ext + ") is not supported. Acceptable extensions: " +
                                    String.join(",", typeExtension.keySet()) + ".");
                            continue;
                        }
                        if(!fname.startsWith("/")) fname = "/" + fname;
                    } else
                        ext  = "";
                    var host = matcher.group("hostname").toLowerCase();
                    int port = 80;
                    if(!matcher.group("port").isEmpty())
                         port = Integer.parseInt(matcher.group("port"));
                    if (verb == HttpVerb.GET)
                        requestsq.add( new Pair<>(generateGetRequest(fname+"."+ext,URI.create(host), BodyHandlers.ofFile.apply(fname)),port));
                    else {
                        if(FileUtils.exists("/client_content"+fname)){
                            requestsq.add(new Pair<>(generatePostRequest(fname,URI.create(host)),port));
                        } else {
                            System.err.printf("File (%s) does not exist, Not going to process request %d\n",fname,lineCount);
                        }
                    }
                } else {
                    System.err.printf("Could not parse line %d, Invalid format\n",lineCount);
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    private static HttpRequest generateGetRequest(String fname, URI host, Function handler){
        return new HttpRequestBuilder(HttpVerb.GET,HttpVersion.HTTP_1_1)
                                .to(URI.create(fname))
                                .withHeader("Host",host.toString())
                                .withHeader("Accept",String.join(",", typeExtension.keySet()))
                                .withHeader("Accept-Language","en-us")
                                .withHeader("User-Agent","Mozilla/4.0")
                                .withHeader("Connection","Keep-Alive")
                                .withBodyHandler(handler)
                                .build();
    }

    private static HttpRequest generatePostRequest(String fname, URI host){
        return new HttpRequestBuilder(HttpVerb.POST, HttpVersion.HTTP_1_1)
                .to(URI.create(fname))
                .withHeader("Host",host.toString())
                .withHeader("Accept",String.join(",", typeExtension.keySet()))
                .withHeader("Accept-Language","en-us")
                .withHeader("User-Agent","Mozilla/4.0")
                .withHeader("Connection","Keep-Alive")
                .withBody(BodyGenerators.fromFile.apply(fname))
                .build();
    }
}
