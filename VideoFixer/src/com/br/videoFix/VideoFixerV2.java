package com.br.videoFix;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class VideoFixerV2 {

    // ====================== CONFIGURAÇÕES ======================
    private static final String INPUT_DIR  = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\input";
    private static final String WORK_DIR   = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\trabalho";
    private static final String OUTPUT_DIR  = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\corrigidos";

    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int MAX_CLEAN_RETRIES = 6;
    private static final long CLEAN_RETRY_DELAY_MS = 600L;
    private static final long FFMPEG_TIMEOUT_MIN = 6L;

    private static final ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    // Log salvo na pasta do projeto
    private static final Path LOG_FILE = Paths.get(System.getProperty("user.dir"), "video_fixer.log");

    // ====================== CORES ANSI ======================
    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_GREEN  = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED    = "\u001B[31m";
    private static final String ANSI_CYAN   = "\u001B[36m";

    // ====================== CONTADORES ======================
    private static final AtomicInteger totalFiles = new AtomicInteger(0);
    private static final AtomicInteger processed  = new AtomicInteger(0);
    private static final AtomicInteger copied     = new AtomicInteger(0);
    private static final AtomicInteger reencoded  = new AtomicInteger(0);
    private static final AtomicInteger failed     = new AtomicInteger(0);

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        log("=== INÍCIO DO PROCESSAMENTO ===");

        Path entrada = Path.of(INPUT_DIR);
        Path trabalho = Path.of(WORK_DIR);
        Path saida = Path.of(OUTPUT_DIR);

        criarDiretorioSeNaoExistir(entrada);
        criarDiretorioSeNaoExistir(trabalho);
        criarDiretorioSeNaoExistir(saida);

        List<Path> arquivos = moverArquivosParaTrabalho(entrada, trabalho);
        if (arquivos.isEmpty()) {
            log("Nenhum arquivo .mp4 encontrado.");
            shutdownAndAwait();
            return;
        }

        totalFiles.set(arquivos.size());
        log("Encontrados " + totalFiles.get() + " arquivos para processar.");

        List<CompletableFuture<Void>> futures = arquivos.stream()
                .map(arquivo -> CompletableFuture.runAsync(() -> processarArquivo(arquivo, saida), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        limparDiretorioComRetry(trabalho);
        limparDiretorioComRetry(entrada);

        shutdownAndAwait();

        long end = System.currentTimeMillis();
        double tempoSeg = (end - start) / 1000.0;

        log("\n=== RESUMO FINAL ===");
        log("Total: " + totalFiles.get());
        log("Copiados (fast start): " + copied.get());
        log("Reencodados (ultrafast): " + reencoded.get());
        log("Falhas: " + failed.get());
        log(String.format("Tempo total: %.2f segundos (%.2f min)", tempoSeg, tempoSeg / 60));
        log("=== FIM ===");
    }

    // ====================== MOVIMENTAÇÃO ======================
    private static List<Path> moverArquivosParaTrabalho(Path src, Path dst) {
        try (var stream = Files.list(src)) {
            return stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".mp4"))
                    .map(p -> {
                        try {
                            Path dest = dst.resolve(p.getFileName());
                            Files.move(p, dest, StandardCopyOption.REPLACE_EXISTING);
                            return dest;
                        } catch (IOException e) {
                            logError("Erro ao mover: " + p.getFileName() + " → " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logError("Erro ao listar diretório de entrada: " + e.getMessage());
            return List.of();
        }
    }

    // ====================== PROCESSAMENTO ======================
    private static void processarArquivo(Path entrada, Path dirSaida) {
        String nome = entrada.getFileName().toString();
        Path saida = dirSaida.resolve(nome);

        try {
            Files.deleteIfExists(saida);
        } catch (IOException e) {
            logError("Não foi possível remover saída antiga: " + saida);
            failed.incrementAndGet();
            atualizarProgresso();
            return;
        }

        boolean sucesso;
        if (tentarModoCopy(entrada, saida)) {
            logSuccess("Copiado (faststart): " + nome);
            copied.incrementAndGet();
            sucesso = true;
        } else if (reencodarComPresetRapido(entrada, saida)) {
            logWarn("Reencodado (ultrafast): " + nome);
            reencoded.incrementAndGet();
            sucesso = true;
        } else {
            logError("FALHA ao processar: " + nome);
            failed.incrementAndGet();
            sucesso = false;
        }

        if (sucesso) {
            tentarLimparEntrada(entrada);
        }

        processed.incrementAndGet();
        atualizarProgresso();
    }

    private static void tentarLimparEntrada(Path arquivo) {
        int tentativa = 0;
        while (tentativa < MAX_CLEAN_RETRIES) {
            try {
                Files.deleteIfExists(arquivo);
                return;
            } catch (IOException ignored) {
                tentativa++;
                if (tentativa == MAX_CLEAN_RETRIES) {
                    logWarn("Arquivo ainda em uso após retries: " + arquivo.getFileName());
                } else {
                    try { Thread.sleep(CLEAN_RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private static boolean tentarModoCopy(Path in, Path out) {
        return executarFFmpeg(new String[]{
                "ffmpeg", "-y",
                "-i", in.toString(),
                "-c", "copy",
                "-movflags", "+faststart",
                out.toString()
        });
    }

    private static boolean reencodarComPresetRapido(Path in, Path out) {
        return executarFFmpeg(new String[]{
                "ffmpeg", "-y",
                "-i", in.toString(),
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-crf", "23",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                out.toString()
        });
    }

    private static boolean executarFFmpeg(String[] cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();

            boolean terminado = p.waitFor(FFMPEG_TIMEOUT_MIN, TimeUnit.MINUTES);
            return terminado && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    // ====================== LIMPEZA FINAL ======================
    private static void limparDiretorioComRetry(Path dir) {
        if (!Files.exists(dir)) return;

        try (var stream = Files.list(dir)) {
            stream.forEach(arquivo -> {
                int tentativa = 0;
                while (tentativa < MAX_CLEAN_RETRIES) {
                    try {
                        if (Files.deleteIfExists(arquivo)) {
                            break;
                        }
                    } catch (IOException e) {
                        tentativa++;
                        if (tentativa == MAX_CLEAN_RETRIES) {
                            logError("Falha definitiva ao limpar: " + arquivo);
                        } else {
                            try { Thread.sleep(CLEAN_RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    }
                }
            });
        } catch (IOException e) {
            logError("Erro ao limpar diretório: " + dir);
        }
    }

    // ====================== UTILIDADES ======================
    private static void criarDiretorioSeNaoExistir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logError("Erro ao criar diretório: " + dir);
        }
    }

    private static void shutdownAndAwait() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ====================== PROGRESSO ======================
    private static void atualizarProgresso() {
        int done = processed.get();
        int total = totalFiles.get();
        if (total == 0) return;

        double pct = (done * 100.0) / total;
        int filled = done * 40 / total;
        String bar = "█".repeat(filled) + "░".repeat(40 - filled);
        System.out.printf("\r%s[%s] %d/%d (%.1f%%)%s", ANSI_CYAN, bar, done, total, pct, ANSI_RESET);
        if (done == total) System.out.println();
    }

    // ====================== LOG SEGURO ======================
    private static void log(String msg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + timestamp + "] " + msg;
        System.out.println(line);
        appendToFileSafely(line + System.lineSeparator());
    }

    private static void logSuccess(String msg) {
        log(ANSI_GREEN + msg + ANSI_RESET);
    }

    private static void logWarn(String msg) {
        log(ANSI_YELLOW + msg + ANSI_RESET);
    }

    private static void logError(String msg) {
        log(ANSI_RED + msg + ANSI_RESET);
    }

    private static void appendToFileSafely(String content) {
        try {
            Path parent = VideoFixerV2.LOG_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(VideoFixerV2.LOG_FILE, content,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("[LOG ERROR] Falha ao escrever no log: " + e.getMessage());
        }
    }
}