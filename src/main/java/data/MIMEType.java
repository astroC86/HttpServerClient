package data;

public enum MIMEType {
    PLAINTEXT, PNG, HTML, BLOB;

    @Override
    public String toString() {
        return switch (this) {
            case PLAINTEXT -> "text/plain";
            case PNG -> "image/png";
            case HTML -> "text/html";
            case BLOB -> "application/octet-stream";
        };
    }
}
