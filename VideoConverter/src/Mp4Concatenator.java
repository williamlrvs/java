import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Mp4Concatenator {

    public static void main(String[] args) {
        String inputDirPath = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\Xvideos RED - fadynha013 - [Pack] [2025] Part 1 - 1080p -_-Negao_762";      // diretório com arquivos .mp4 já convertidos
        String finalDirPath = "D:\\Downloads\\saida_final"; // diretório onde os 10 arquivos finais serão salvos
        int totalFinalFiles = 10; // número de arquivos finais desejados

        try {
            // A mensagem de sucesso foi movida para dentro de concatenateMp4Files
            concatenateMp4Files(inputDirPath, finalDirPath, totalFinalFiles);

            // REMOVIDA: A linha "System.out.println("\n✅ Concatenação concluída! 10 arquivos finais gerados em: " + finalDirPath);"
            // pois era ela que causava a impressão indevida.

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void concatenateMp4Files(String inputDirPath, String finalDirPath, int totalFinalFiles) throws IOException, InterruptedException {
        File inputDir = new File(inputDirPath);
        File finalDir = new File(finalDirPath);

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Diretório de entrada inválido: " + inputDirPath);
        }
        if (!finalDir.exists()) finalDir.mkdirs();

        File[] mp4Files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
        if (mp4Files == null || mp4Files.length == 0) {
            System.out.println("⚠ Nenhum arquivo .mp4 encontrado no diretório de entrada.");
            return;
            // Se retornar aqui, a execução do restante da função e a mensagem de sucesso serão ignoradas.
        }

        // Ordena arquivos por nome
        Arrays.sort(mp4Files);

        // Divide arquivos em grupos para gerar os 10 finais
        int filesPerGroup = (int) Math.ceil(mp4Files.length / (double) totalFinalFiles);
        int filesGenerated = 0; // Contador para saber quantos arquivos foram realmente gerados

        for (int i = 0; i < totalFinalFiles; i++) {
            int start = i * filesPerGroup;
            int end = Math.min(start + filesPerGroup, mp4Files.length);

            if (start >= mp4Files.length) break; // não há mais arquivos para processar

            List<File> groupFiles = new ArrayList<>();
            for (int j = start; j < end; j++) {
                groupFiles.add(mp4Files[j]);
            }

            // Cria arquivo temporário de lista para FFmpeg
            File listFile = new File(finalDir, "list_" + i + ".txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(listFile))) {
                for (File f : groupFiles) {
                    writer.write("file '" + f.getAbsolutePath().replace("\\", "/") + "'");
                    writer.newLine();
                }
            }

            String outputFilePath = finalDirPath + File.separator + "final_" + (i + 1) + ".mp4";
            String[] command = {
                    "ffmpeg",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.getAbsolutePath(),
                    "-c", "copy",
                    "-y",
                    outputFilePath
            };

            runProcess(command);
            listFile.delete(); // remove lista temporária
            System.out.println("✔ Arquivo final gerado: " + outputFilePath);
            filesGenerated++; // Incrementa o contador
        }

        // ADICIONADA: A mensagem de sucesso foi movida para cá, usando o contador real.
        System.out.println("\n✅ Concatenação concluída! " + filesGenerated + " arquivos finais gerados em: " + finalDirPath);
    }

    // Método para executar comandos FFmpeg
    public static void runProcess(String[] command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Para evitar a poluição excessiva no console, a impressão das linhas do FFmpeg
                // foi comentada. Se precisar ver a saída do FFmpeg, descomente a linha abaixo:
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Erro no comando: " + String.join(" ", command) + " (código de saída: " + exitCode + ")");
        }
    }
}