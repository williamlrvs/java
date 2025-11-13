package com.br.code.util;

import com.br.code.config.ConsorcioConfig;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.List;

public class DateUtils {
    public static LocalDate findLatestDrawBeforeDate(List<JSONObject> draws, LocalDate target) {
        return draws.stream()
                .map(d -> LocalDate.parse(d.getString("data"), ConsorcioConfig.getDateFormatter()))
                .filter(d -> d.isBefore(target))
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    public static JSONObject findDrawByDate(List<JSONObject> draws, LocalDate target) {
        String s = target.format(ConsorcioConfig.getDateFormatter());
        return draws.stream()
                .filter(d -> d.getString("data").equals(s))
                .findFirst()
                .orElse(null);
    }
}