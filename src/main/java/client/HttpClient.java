package client;

import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import handlers.BodyGenerators;
import handlers.BodyHandlers;

import java.io.BufferedReader;
import java.io.File;
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
                                                            "(?:(?:(?<identity>\\/)(?:[^\\w]+))|(?:(?<fname>.*?)(?:\\.(?<ext>[^.]\\w+))))"+
                                                            "(?:\\s+)" +
                                                            "(?<hostname>(?:(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|" +
                                                                        "(?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)+(?:[A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])))" +
                                                            "(?:\\s+)?(?<port>\\d+)?$");

    public static void main(String[] args) throws IOException {
        if(args.length == 0 ){
            System.err.println("Please provide the path to the commands file.");
            System.exit(-1);
        }
        String      commandsFile   = args[0];
        Path        fileName       = Path.of(commandsFile);
        if(!fileName.toFile().exists()){
            System.err.println("The provided path to the commands file does not exist.");
            System.exit(-1);
        }
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
                            System.err.println("Line "+line+": Extension(" + ext + ") is not supported. Acceptable extensions: " +
                                    String.join(",", typeExtension.keySet()) + ".");
                            continue;
                        }
                        if(!fname.startsWith("/")) fname = "/" + fname;
                    } else {
                        if (verb == HttpVerb.POST){
                            System.err.printf("Line %d: Extension missing from file (%s).",lineCount,fname);
                            continue;
                        }
                    }
                    fname = fname +"."+ext;
                    var host = matcher.group("hostname").toLowerCase();
                    int port = 80;
                    if(matcher.group("port")!=null)
                         port = Integer.parseInt(matcher.group("port"));
                    if (verb == HttpVerb.GET)
                        requestsq.add( new Pair<>(generateGetRequest(fname,URI.create(host),BodyHandlers.ofFile.apply(fname)), port));
                    else {
                        if(new File("./client_content/"+fname).exists()){
                            requestsq.add(new Pair<>(generatePostRequest(fname,ext,URI.create(host)),port));
                        } else {
                            System.err.printf("Line %d: File (%s) does not exist.\n",lineCount,fname);
                        }
                    }
                } else {
                    System.err.printf("Could not parse line %d, Invalid format.\n",lineCount);
                }
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        var client  = new HttpClientThread(requestsq);
    }

    public static HttpRequest generateGetRequest(String fname, URI host, Function handler){
        return new HttpRequestBuilder(HttpVerb.GET,HttpVersion.HTTP_1_1)
                                .to(URI.create(fname))
                                .withHeader("Host",host.toString())
                                .withHeader("Accept",String.join(",", typeExtension.keySet()))
                                .withHeader("Accept-Language","en-us")
                                .withHeader("User-Agent","Mozilla/4.0")
                                .withBodyHandler(handler)
                                .build();
    }

    private static HttpRequest generatePostRequest(String fname, String ext, URI host){
        return new HttpRequestBuilder(HttpVerb.POST, HttpVersion.HTTP_1_1)
                                .to(URI.create(fname))
                                .withHeader("Host",host.toString())
                                .withHeader("Accept",String.join(",", typeExtension.keySet()))
                                .withHeader("Accept-Language","en-us")
                                .withHeader("User-Agent","Mozilla/4.0")
                                .withBody(typeExtension.inverse().get(ext),BodyGenerators.fromFile.apply(fname))
                                .build();
    }
}
