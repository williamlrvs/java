package com.fii.dashboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    @Value("${app.config.min_dy:0.08}")
    private double minDy;

    @Value("${app.config.max_pvp:1.0}")
    private double maxPvp;

    @Value("${app.config.min_volume:100000}")
    private long minVolume;

    // === MÉTODOS GETTER ===
    public double getDouble(String key, double defaultValue) {
        return switch (key) {
            case "min_dy" -> minDy;
            case "max_pvp" -> maxPvp;
            default -> defaultValue;
        };
    }

    public long getLong(String key, long defaultValue) {
        return switch (key) {
            case "min_volume" -> minVolume;
            default -> defaultValue;
        };
    }

    // === MÉTODO getString() (OBRIGATÓRIO) ===
    public String getString(String key, String defaultValue) {
        return switch (key) {
            case "min_dy" -> String.valueOf(minDy);
            case "max_pvp" -> String.valueOf(maxPvp);
            case "min_volume" -> String.valueOf(minVolume);
            default -> defaultValue;
        };
    }


}