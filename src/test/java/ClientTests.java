import client.HttpClientThread;
import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import handlers.BodyHandlers;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;

import static client.HttpClient.generateGetRequest;
import static utils.Content.typeExtension;

public class ClientTests {

    @Test
    public void client_should_retry_on_failure(){
        var requestq = new ArrayDeque<Pair<HttpRequest,Integer>>();

        var closeReq =  new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                                            .to(URI.create("/"))
                                            .withHeader("Host","github.com")
                                            .withHeader("Accept",String.join(",", typeExtension.keySet()))
                                            .withHeader("Accept-Language","en-us")
                                            .withHeader("User-Agent","Mozilla/4.0")
                                            .withHeader("Connection","Close")
                                            .build();

        requestq.add(new Pair<>(generateGetRequest("/", URI.create("github.com"), BodyHandlers.ofStream),80));
        requestq.add(new Pair<>(closeReq,80));
        requestq.add(new Pair<>(generateGetRequest("/", URI.create("github.com"), BodyHandlers.ofStream),80));
        var client   = new HttpClientThread(requestq);
    }
}
