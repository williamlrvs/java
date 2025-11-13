package com.br.code.util;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class JsonUtils {
    public static List<JSONObject> parseJsonSafely(String json) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getJSONObject(i));
            }
        } catch (Exception e) {
            System.err.println("JSON inválido: " + e.getMessage());
        }
        return list;
    }

    public static List<String> parsePrizes(JSONObject o) {
        List<String> list = new ArrayList<>();
        JSONArray a = o.getJSONArray("dezenas");
        for (int i = 0; i < a.length(); i++) {
            list.add(String.format("%06d", Integer.parseInt(a.getString(i))));
        }
        return list;
    }
}