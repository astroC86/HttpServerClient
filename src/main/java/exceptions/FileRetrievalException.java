package exceptions;

import java.io.IOException;

public class FileRetrievalException extends IOException {
    public FileRetrievalException(String message) {
        super(message);
    }
}
