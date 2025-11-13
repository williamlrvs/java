package com.fii.dashboard.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class BackupService {
    private static final Path DB = Path.of("src/main/resources/data/fii_database.db");
    private static final Path DIR = Path.of("src/main/resources/backups");

    @Scheduled(cron = "0 0 2 * * *")
    public void backup() {
        try {
            Files.createDirectories(DIR);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dest = DIR.resolve("fii_backup_" + ts + ".db");
            Files.copy(DB, dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Backup: " + dest);
            limparAntigos();
        } catch (IOException e) {
            System.err.println("Backup falhou: " + e.getMessage());
        }
    }

    private void limparAntigos() throws IOException {
        try (var s = Files.list(DIR)) {
            s.filter(p -> {
                try {
                    return Files.getLastModifiedTime(p).toInstant()
                            .isBefore(java.time.Instant.now().minus(java.time.Duration.ofDays(7)));
                } catch (IOException ex) { return false; }
            }).forEach(p -> {
                try { Files.delete(p); } catch (IOException ex) {}
            });
        }
    }
}