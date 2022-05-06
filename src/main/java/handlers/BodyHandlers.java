package handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BodyHandlers {
    public static Function<byte[],String> ofString = byteBuffer -> {
        var sb = new StringBuilder();
        for(var b: byteBuffer){
            sb.append(b);
        }
        return sb.toString();
    };

    public static Function<String, Function<byte[],File>> ofFile = fname->(byteBuffer)->{
        var outputFile =  new File("client_content/"+fname);
        try {
            Files.write(outputFile.toPath(), byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputFile;
    };

}
