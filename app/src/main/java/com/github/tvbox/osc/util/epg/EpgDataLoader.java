package com.github.tvbox.osc.util.epg;

import android.content.Context;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class EpgDataLoader {
    private static Map<String, String> nameToEpgId = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized void load(Context context) {
        if (loaded) return;
        try {
            InputStream is = context.getAssets().open("epg_data.json");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonArray epgs = root.getAsJsonArray("epgs");
            for (int i = 0; i < epgs.size(); i++) {
                JsonObject item = epgs.get(i).getAsJsonObject();
                String epgid = item.get("epgid").getAsString();
                String names = item.get("name").getAsString();
                for (String name : names.split(",")) {
                    name = name.trim();
                    if (!name.isEmpty()) nameToEpgId.put(name, epgid);
                }
            }
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getEpgId(String channelName) {
        return nameToEpgId.get(channelName);
    }

    public static Map<String, String> getAllMappings() {
        return new HashMap<>(nameToEpgId);
    }
}
