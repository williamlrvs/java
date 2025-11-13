package com.br.videoFix;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class VideoFixer {

    // Diretórios hardcoded
    private static final String INPUT_DIR = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\input";
    private static final String WORK_DIR  = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\trabalho";
    private static final String OUTPUT_DIR = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\corrigidos";

    public static void main(String[] args) {
        File entrada = new File(INPUT_DIR);
        File trabalho = new File(WORK_DIR);
        File saida = new File(OUTPUT_DIR);

        criarDiretorioSeNaoExistir(entrada);
        criarDiretorioSeNaoExistir(trabalho);
        criarDiretorioSeNaoExistir(saida);

        // 1. Move arquivos de entrada para diretório de trabalho
        File[] arquivos = entrada.listFiles((dir, nome) -> nome.toLowerCase().endsWith(".mp4"));
        if (arquivos == null || arquivos.length == 0) {
            System.out.println("Nenhum arquivo MP4 encontrado no diretório de entrada.");
            return;
        }

        for (File arquivo : arquivos) {
            File destino = new File(WORK_DIR + "\\" + arquivo.getName());
            try {
                Files.move(arquivo.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Erro ao mover arquivo para diretório de trabalho: " + arquivo.getName());
            }
        }

        // 2. Processa arquivos do diretório de trabalho e salva no de saída
        File[] arquivosTrabalho = trabalho.listFiles((dir, nome) -> nome.toLowerCase().endsWith(".mp4"));
        if (arquivosTrabalho == null || arquivosTrabalho.length == 0) {
            System.out.println("Nenhum arquivo MP4 encontrado no diretório de trabalho.");
            return;
        }
        for (File arquivo : arquivosTrabalho) {
            corrigirVideo(arquivo);
        }

        // 3. Limpa diretório de trabalho
        limparDiretorio(trabalho);

        // 4. Garante que diretório de entrada está vazio
        limparDiretorio(entrada);

        System.out.println("Processamento concluído. Apenas o diretório de saída deve conter os arquivos finais.");
    }

    private static void corrigirVideo(File arquivoEntrada) {
        String nomeSaida = VideoFixer.OUTPUT_DIR + "\\" + arquivoEntrada.getName();

        // Remove o arquivo de saída se já existir
        File arquivoSaida = new File(nomeSaida);
        if (arquivoSaida.exists()) {
            if (!arquivoSaida.delete()) {
                System.err.println("Não foi possível remover o arquivo de saída existente: " + nomeSaida);
                return;
            }
        }

        // Comando ffmpeg para modo full (re-encodificação)
        String[] comando = {
                "ffmpeg",
                "-i", arquivoEntrada.getAbsolutePath(),
                "-c:v", "libx264",   // codec de vídeo H.264
                "-c:a", "aac",      // codec de áudio AAC
                "-movflags", "+faststart",
                nomeSaida
        };

        try {
            System.out.println("Corrigindo (modo full): " + arquivoEntrada.getName());
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.inheritIO();
            Process processo = pb.start();
            processo.waitFor();
            System.out.println("Corrigido: " + arquivoEntrada.getName());
        } catch (IOException | InterruptedException e) {
            System.err.println("Erro ao processar " + arquivoEntrada.getName() + ": " + e.getMessage());
        }
    }

    private static void limparDiretorio(File dir) {
        File[] arquivos = dir.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (!arquivo.delete()) {
                    System.err.println("Falha ao limpar arquivo: " + arquivo.getAbsolutePath());
                }
            }
        }
    }

    // Método utilitário para criar diretório se não existir
    private static void criarDiretorioSeNaoExistir(File dir) {
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Diretório criado: " + dir.getAbsolutePath());
            } else {
                System.err.println("Não foi possível criar o diretório: " + dir.getAbsolutePath());
            }
        }
    }
}