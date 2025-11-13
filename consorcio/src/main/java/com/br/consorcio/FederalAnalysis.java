package com.br.consorcio;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Análise estatística da Loteria Federal com exportação Excel + CSV + gráfico Top20
 * Compatível Java 17 / IntelliJ IDEA / Maven
 */
public class FederalAnalysis {

    private static final String API_URL = "https://loteriascaixa-api.herokuapp.com/api/federal";
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    private record Draw(LocalDate date, List<String> premios) {}

    private static LocalDate parseDate(String s) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static List<Draw> fetchDraws(LocalDate from, LocalDate to) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Erro ao acessar API: " + response.statusCode());
        }

        JSONArray arr = new JSONArray(response.body());
        List<Draw> draws = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            // Parse data
            String rawDate = obj.optString("dataApuracao",
                    obj.optString("data",
                            obj.optString("dataSorteio", null)));
            LocalDate date = parseDate(rawDate);

            if (date == null || date.isBefore(from) || date.isAfter(to)) {
                continue;
            }

            List<String> premios = new ArrayList<>();

            // **CORREÇÃO PRINCIPAL**: Melhor extração dos prêmios
            if (obj.has("premiacoes")) {
                JSONArray premiacoesArr = obj.getJSONArray("premiacoes");
                for (int j = 0; j < Math.min(5, premiacoesArr.length()); j++) {
                    JSONObject p = premiacoesArr.getJSONObject(j);
                    String numero = p.optString("numero", "").trim();
                    if (!numero.isEmpty()) {
                        premios.add(numero);
                    }
                }
            }

            // Fallback alternativo
            if (premios.isEmpty() && obj.has("premios")) {
                JSONArray premiosArr = obj.getJSONArray("premios");
                for (int j = 0; j < Math.min(5, premiosArr.length()); j++) {
                    JSONObject p = premiosArr.getJSONObject(j);
                    String numero = p.optString("numero", "").trim();
                    if (!numero.isEmpty()) {
                        premios.add(numero);
                    }
                }
            }

            // Outro fallback
            if (premios.isEmpty() && obj.has("listaDezenas")) {
                JSONArray dezenasArr = obj.getJSONArray("listaDezenas");
                for (int j = 0; j < Math.min(5, dezenasArr.length()); j++) {
                    String numero = dezenasArr.getString(j).trim();
                    if (!numero.isEmpty()) {
                        premios.add(numero);
                    }
                }
            }

            // Debug: imprimir o que foi extraído
            if (!premios.isEmpty()) {
                System.out.println("Concurso " + obj.optInt("concurso", -1) +
                        " - Data: " + date +
                        " - Prêmios: " + premios);
            }

            if (premios.size() >= 5) {
                draws.add(new Draw(date, premios.subList(0, 5)));
            }
        }

        draws.sort(Comparator.comparing(Draw::date));
        return draws;
    }


    private static List<String> extractHundreds(String prize) {
        String s = String.format("%05d", Integer.parseInt(prize));
        return List.of(s.substring(2), s.substring(1, 4), s.substring(0, 3));
    }

    private static void analyzeAndExport(List<Draw> draws, String target, String saveXlsx) throws IOException {
        List<String> allHundreds = new ArrayList<>();
        for (Draw d : draws) {
            for (String prize : d.premios()) {
                allHundreds.addAll(extractHundreds(prize));
            }
        }

        int total = allHundreds.size();
        Map<String, Integer> freq = new HashMap<>();
        for (String c : allHundreds)
            freq.put(c, freq.getOrDefault(c, 0) + 1);

        List<String> allPossible = new ArrayList<>();
        for (int i = 0; i < 1000; i++) allPossible.add(String.format("%03d", i));

        List<Map<String, Object>> data = new ArrayList<>();
        for (String c : allPossible) {
            int count = freq.getOrDefault(c, 0);
            double prob = total > 0 ? (count * 100.0 / total) : 0.0;
            Map<String, Object> row = new HashMap<>();
            row.put("centena", c);
            row.put("ocorrencias", count);
            row.put("probabilidade", prob);
            data.add(row);
        }

        data.sort((a, b) -> Integer.compare((int) b.get("ocorrencias"), (int) a.get("ocorrencias")));

        Optional<Map<String, Object>> targetRow = data.stream()
                .filter(r -> r.get("centena").equals(target))
                .findFirst();

        System.out.println("\n=== ANÁLISE DAS CENTENAS ===");
        System.out.printf("Sorteios analisados: %d%n", draws.size());
        System.out.printf("Total de centenas: %d%n", total);
        if (targetRow.isPresent()) {
            Map<String, Object> r = targetRow.get();
           System.out.printf("Centena %s: %d ocorrências (%.4f%%)%n",target, r.get("ocorrencias"), r.get("probabilidade"));
        } else {
            System.out.println("Centena " + target + " não apareceu em nenhum sorteio.");
        }

        exportToExcel(data, target, saveXlsx);
        exportToCsv(data, saveXlsx.replace(".xlsx", ".csv"));
    }

    /** Exporta Excel + gráfico */
    private static void exportToExcel(List<Map<String, Object>> data, String target, String filePath) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();

        // ---- ABA PRINCIPAL ----
        XSSFSheet sheet = wb.createSheet("Centenas");
        String[] cols = {"centena", "ocorrencias", "probabilidade (%)"};

        Row header = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

        CellStyle highlight = wb.createCellStyle();
        highlight.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        highlight.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowIdx = 1;
        for (Map<String, Object> row : data) {
            Row r = sheet.createRow(rowIdx++);
            String cVal = (String) row.get("centena");
            int count = (int) row.get("ocorrencias");
            double prob = (double) row.get("probabilidade");

            Cell c1 = r.createCell(0);
            Cell c2 = r.createCell(1);
            Cell c3 = r.createCell(2);
            c1.setCellValue(cVal);
            c2.setCellValue(count);
            c3.setCellValue(prob);
            if (cVal.equals(target)) for (Cell c : List.of(c1, c2, c3)) c.setCellStyle(highlight);
        }
        for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);

        // ---- ABA TOP20 COM GRÁFICO ----
        XSSFSheet topSheet = wb.createSheet("Top20");
        Row h2 = topSheet.createRow(0);
        h2.createCell(0).setCellValue("centena");
        h2.createCell(1).setCellValue("ocorrencias");

        List<Map<String, Object>> top20 = data.subList(0, Math.min(20, data.size()));
        for (int i = 0; i < top20.size(); i++) {
            Map<String, Object> r = top20.get(i);
            Row rr = topSheet.createRow(i + 1);
            rr.createCell(0).setCellValue((String) r.get("centena"));
            rr.createCell(1).setCellValue((int) r.get("ocorrencias"));
        }

        XSSFDrawing drawing = topSheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(3); anchor.setRow1(1); anchor.setCol2(10); anchor.setRow2(20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Top 20 Centenas Mais Sorteadas");
        chart.setTitleOverlay(false);

        XDDFCategoryAxis xAxis = chart.createCategoryAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.BOTTOM);
        XDDFValueAxis yAxis = chart.createValueAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.LEFT);
        xAxis.setTitle("Centenas"); yAxis.setTitle("Ocorrências");

        XDDFDataSource<String> xs = XDDFDataSourcesFactory.fromStringCellRange(topSheet,
                new CellRangeAddress(1, top20.size(), 0, 0));
        XDDFNumericalDataSource<Double> ys = XDDFDataSourcesFactory.fromNumericCellRange(topSheet,
                new CellRangeAddress(1, top20.size(), 1, 1));

        XDDFChartData dataBar = chart.createData(org.apache.poi.xddf.usermodel.chart.ChartTypes.BAR, xAxis, yAxis);
        dataBar.addSeries(xs, ys);
        chart.plot(dataBar);

        for (int i = 0; i < 2; i++) topSheet.autoSizeColumn(i);

        // ---- SALVAR ARQUIVO ----
        try (FileOutputStream fos = new FileOutputStream(Paths.get(filePath).toFile())) {
            wb.write(fos);
        }
        wb.close();
        System.out.println("\n📊 Arquivo Excel salvo em: " + filePath);
    }

    /** Exportação CSV simples */
    private static void exportToCsv(List<Map<String, Object>> data, String filePath) throws IOException {
        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write("centena,ocorrencias,probabilidade (%)\n");
            for (Map<String, Object> row : data) {
                fw.write(String.format("%s,%d,%.6f%n",
                        row.get("centena"), row.get("ocorrencias"), row.get("probabilidade")));
            }
        }
        System.out.println("📄 Arquivo CSV salvo em: " + filePath);
    }

    public static void main(String[] args) {
        String target = "074";
        String saveXlsx = "resultados.xlsx";
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.now();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--target" -> target = args[++i];
                case "--save-xlsx" -> saveXlsx = args[++i];
            }
        }

        try {
            List<Draw> draws = fetchDraws(from, to);
            if (draws.isEmpty()) {
                System.out.println("Nenhum sorteio encontrado.");
                return;
            }
            analyzeAndExport(draws, target, saveXlsx);
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
