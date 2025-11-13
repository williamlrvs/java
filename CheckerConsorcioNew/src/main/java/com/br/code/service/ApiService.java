package com.br.code.service;

import com.br.code.util.NetworkUtils;

public class ApiService {
    public String fetchLatest() {
        try {
            return NetworkUtils.getLotteryResults(com.br.code.config.ConsorcioConfig.getBaseApiUrl() + "latest");
        } catch (Exception e) {
            System.err.println("Erro na API latest: " + e.getMessage());
            return null;
        }
    }

    public String fetchHistorical() {
        try {
            return NetworkUtils.getCachedHistoricalResults();
        } catch (Exception e) {
            System.err.println("Erro na API historical: " + e.getMessage());
            return null;
        }
    }
}