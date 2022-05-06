package exceptions;

import java.io.IOException;

public class FileCreationException extends IOException {
    public FileCreationException(String message) {
        super(message);
    }
}
