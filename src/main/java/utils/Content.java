package utils;

import collections.BiMap;

public class Content {
    public static final BiMap<String, String> typeExtension = new BiMap<>();
    static {
        typeExtension.put("image/png", "png");
        typeExtension.put("text/html", "html");
        typeExtension.put("text/plain", "txt");
    }
}
