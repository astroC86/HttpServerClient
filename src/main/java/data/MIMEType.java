package data;

public enum MIMEType {
    PLAINTEXT, PNG, HTML;

    @Override
    public String toString() {
        return switch (this) {
            case PLAINTEXT -> "text/plain";
            case PNG -> "image/png";
            case HTML -> "text/html";
        };
    }
}
