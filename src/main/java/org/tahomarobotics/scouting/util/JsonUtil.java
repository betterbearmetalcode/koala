package org.tahomarobotics.scouting.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;

public class JsonUtil {
    public static HashMap<String, Object> getHashMapFromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, new TypeToken<HashMap<String, Object>>(){}.getType());
    }

    public static String getJsonFromHashMap(HashMap<String, Object> hashMap) {
        Gson gson = new Gson();
        return gson.toJson(hashMap);
    }
}