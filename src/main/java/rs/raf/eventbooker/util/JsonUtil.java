package rs.raf.eventbooker.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonUtil {
    // ISO datetime format ako ga budeš kasnije koristio
    public static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create();

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }
}
