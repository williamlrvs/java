package com.br.code.util;

import com.br.code.config.ConsorcioConfig;
import com.br.code.model.AssemblyData;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static List<AssemblyData> readAssemblySorteados() {
        List<AssemblyData> list = new ArrayList<>();
        Path p = Paths.get(ConsorcioConfig.getAssembleiasFile());
        if (!Files.exists(p)) return list;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("dataAssembleia")) continue;
                String[] parts = line.split(";");
                if (parts.length >= 2 && parts[0].matches("\\d{8}")) {
                    LocalDate d = LocalDate.parse(parts[0], ConsorcioConfig.getYyyyMmDdFormatter());
                    Integer s = parts[1].isEmpty() ? null : Integer.parseInt(parts[1]);
                    Integer v = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : null;
                    list.add(new AssemblyData(d, s, v));
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler arquivo: " + e.getMessage());
        }
        return list;
    }
}