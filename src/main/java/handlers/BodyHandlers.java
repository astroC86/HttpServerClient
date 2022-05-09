package handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
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
        var outputFile =  new File("./client_content/"+fname);
        try {
            var outputPath  = outputFile.toPath().getParent();
            if(!new File(String.valueOf(outputPath)).exists()){
                boolean created = new File(String.valueOf(outputPath)).mkdirs();
                if (!created)
                    throw new RuntimeException("Failed to create directory for file");
            }
            Files.write(outputFile.toPath(), byteBuffer);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return outputFile;
    };

    public static Function<OutputStream, Function<byte[],Void>> ofStream = out->(byteBuffer)->{
        try {
            out.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };
}
