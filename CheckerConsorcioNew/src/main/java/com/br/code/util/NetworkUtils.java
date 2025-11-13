package com.br.code.util;

import com.br.code.config.ConsorcioConfig;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class NetworkUtils {
    public static String getLotteryResults(String urlStr) throws IOException, InterruptedException {
        ExponentialBackoff b = new ExponentialBackoff();
        for (int i = 0; ; i++) {
            try {
                URL u = new URL(urlStr);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) {
                    if (i == 2) return null;
                    b.sleep();
                    continue;
                }
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = in.readLine()) != null) sb.append(l);
                in.close();
                c.disconnect();
                return sb.toString();
            } catch (IOException e) {
                if (i == 2) throw e;
                b.sleep();
            }
        }
    }

    public static String getCachedHistoricalResults() throws IOException, InterruptedException {
        Path p = Paths.get(ConsorcioConfig.getCacheFile());
        if (Files.exists(p)) {
            LocalDateTime m = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
            if (LocalDateTime.now().minusHours(ConsorcioConfig.getCacheValidityHours()).isBefore(m)) {
                System.out.println("Cache carregado.");
                return Files.readString(p);
            }
        }
        String r = getLotteryResults(ConsorcioConfig.getBaseApiUrl());
        if (r != null) Files.writeString(p, r);
        return r;
    }

    public static class ExponentialBackoff {
        private long delay = 1000;
        public void sleep() throws InterruptedException { Thread.sleep(delay); delay *= 2; }
    }
}