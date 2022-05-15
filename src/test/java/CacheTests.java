import client.HttpClientThread;
import collections.Pair;
import data.HttpRequest;
import data.HttpVerb;
import data.HttpVersion;
import data.builders.HttpRequestBuilder;
import handlers.BodyHandlers;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayDeque;

import static client.HttpClient.generateGetRequest;
import static utils.Content.typeExtension;

public class CacheTests {
    @Test
    public void should_use_data_from_cache(){
            var requestq = new ArrayDeque<Pair<HttpRequest,Integer>>();
            var host     = "127.0.0.1";
            var port     = 80;
            var out      = new ByteArrayOutputStream();

            var closeReq =  new HttpRequestBuilder(HttpVerb.GET, HttpVersion.HTTP_1_1)
                    .to(URI.create("/z.txt"))
                    .withHeader("Host",host)
                    .withHeader("Accept",String.join(",", typeExtension.keySet()))
                    .withHeader("Accept-Language","en-us")
                    .withHeader("User-Agent","Mozilla/4.0")
                    .withHeader("Connection","Close")
                    .build();
            requestq.add(new Pair<>(generateGetRequest("/some_text_file.txt", URI.create(host), BodyHandlers.ofStream.apply(out)),port));
            requestq.add(new Pair<>(closeReq,port));
            requestq.add(new Pair<>(generateGetRequest("/z.txt", URI.create(host), BodyHandlers.ofStream.apply(out)),port));
            var client   = new HttpClientThread(requestq);
    }
}
