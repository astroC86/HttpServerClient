package data;

public enum HttpVersion {
    HTTP_1_1(1,1),
    HTTP_1_0(1,0);
    private final int minor;
    private final int major;
    HttpVersion(int major, int minor){
        this.minor = minor;
        this.major = major;
    }

    public int major() {
        return major;
    }
    public int minor(){
        return minor;
    }
    @Override
    public String toString(){
        return String.format("HTTP/%d.%d",major,minor);
    }

}
