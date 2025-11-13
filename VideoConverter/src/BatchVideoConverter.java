import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class BatchVideoConverter {

    public static void main(String[] args) {
        // Diretórios de entrada e saída
        String inputDir = "D:\\Downloads\\Nova pasta\\Nova pasta\\1\\2\\3\\4\\5\\6\\7\\8\\9\\10\\11\\12\\13\\14\\15\\16\\17\\18\\19\\20\\21\\22\\23\\24\\25\\26\\27\\28\\29\\30\\Xvideos RED - fadynha013 - [Pack] [2025] Part 1 - 1080p -_-Negao_762";
        String outputDir = "D:\\Downloads\\saida";

        try {
            int totalConvertidos = convertAllTsFiles(inputDir, outputDir);

            if (totalConvertidos > 0) {
                System.out.println("\n✅ Conversão concluída com sucesso! Total de arquivos convertidos: " + totalConvertidos);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int convertAllTsFiles(String inputDirPath, String outputDirPath) throws IOException, InterruptedException {
        File inputDir = new File(inputDirPath);
        File outputDir = new File(outputDirPath);

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Diretório de entrada inválido: " + inputDirPath);
        }

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                throw new IOException("Falha ao criar diretório de saída: " + outputDirPath);
            }
        }

        File[] tsFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ts"));

        if (tsFiles == null || tsFiles.length == 0) {
            System.out.println("⚠ Nenhum arquivo .ts encontrado no diretório: " + inputDirPath);
            return 0;
        }

        int contador = 0;

        for (File tsFile : tsFiles) {
            String baseName = tsFile.getName().replaceAll("(?i)\\.ts$", "");
            String outputFilePath = outputDirPath + File.separator + baseName + ".mp4";

            System.out.println("\n🎬 Convertendo: " + tsFile.getName());
            convertTsToMp4(tsFile.getAbsolutePath(), outputFilePath);
            System.out.println("✔ Arquivo convertido: " + outputFilePath);

            // 🔥 Após conversão bem-sucedida, tenta excluir o arquivo original
            if (tsFile.delete()) {
                System.out.println("🗑 Arquivo original removido: " + tsFile.getName());
            } else {
                System.out.println("⚠ Não foi possível remover o arquivo original: " + tsFile.getName());
            }

            contador++;
        }

        return contador;
    }

    public static void convertTsToMp4(String inputPath, String outputPath) throws IOException, InterruptedException {
        String[] command = {
                "ffmpeg",
                "-i", inputPath,
                "-c", "copy",
                "-y", // sobrescreve se já existir
                outputPath
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Erro ao converter " + inputPath + " (código de saída: " + exitCode + ")");
        }
    }
}
