package handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

public class BodyGenerators {
    public static Function<String,byte[]> fromFile = (fname) -> {
        try {
            return Files.readAllBytes(Paths.get("./client_content/"+fname));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };
}
