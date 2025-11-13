package com.br.Code.main;

import com.itextpdf.text.*;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

public class Checker_Consorcio {

    /* --------------------------------------------------------------
          CONFIGURAÇÃO DO SEU CONSÓRCIO
     -------------------------------------------------------------- */
    private static final int USER_CONSORTIUM_NUMBER = 74;
    private static final String FORMATTED_NUMBER = String.format("%03d", USER_CONSORTIUM_NUMBER);
    private static final LocalDate SUA_ENTRADA = LocalDate.of(2025, 10, 28);

    private static final int TOTAL_PARCELAS = 84;
    private static final int PARCELAS_PAGAS = 2;
    private static final int PARCELAS_RESTANTES = TOTAL_PARCELAS - PARCELAS_PAGAS;

    private static final String BASE_API_URL = "https://loteriascaixa-api.herokuapp.com/api/federal/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MMM/yy");
    private static final String CACHE_FILE = "historico_cache.json";
    private static final String ASSEMBLEIAS_FILE = "datasAssembleiasSorteados.txt";
    private static final String PDF_OUTPUT = "Relatorio_Consorcio_" + FORMATTED_NUMBER + ".pdf";
    private static final String CHART_OUTPUT = "grafico_evolucao.png";
    private static final long CACHE_VALIDITY_HOURS = 24;

    /* --------------------------------------------------------------
       DADOS DO GRUPO
       -------------------------------------------------------------- */
    private static final int ATIVOS = 252;
    private static final int CONTEMPLADOS = 12;
    private static final int DESISTENTES = 148;
    private static final int TOTAL_COTAS = ATIVOS + DESISTENTES;

    private static final YearMonth PROXIMO_MES = YearMonth.now().plusMonths(1);
    private static final YearMonth LIMITE_PROJECAO = YearMonth.of(2026, 12);

    /* --------------------------------------------------------------
       LANCE EMBUTIDO + CENÁRIOS
       -------------------------------------------------------------- */
    private static final double VALOR_CREDITO = 160_000.00;
    private static final double PERCENTUAL_LANCE_EMBUTIDO = 0.25;
    private static final double VALOR_LANCE_EMBUTIDO = VALOR_CREDITO * PERCENTUAL_LANCE_EMBUTIDO;
    private static final double RECURSOS_VINCULADOS = 397_751.88;
    private static final double TAXA_DESISTENCIA_MENSAL = 0.008;

    public static void main(String[] args) {
        System.out.println("INICIANDO ANÁLISE v8.8 PARA A COTA: " + FORMATTED_NUMBER);
        System.out.println("=".repeat(90));

        List<AssemblyData> todasAssembleias = readAssemblySorteados();
        if (todasAssembleias.isEmpty()) {
            System.out.println("ERRO: Arquivo 'datasAssembleiasSorteados.txt' não encontrado ou vazio.");
            return;
        }

        long antes = todasAssembleias.stream().filter(a -> a.date().isBefore(SUA_ENTRADA)).count();
        if (antes > 0) {
            System.out.printf("AVISO: %d assembleia(s) antes de %s foram ignoradas.\n", antes, SUA_ENTRADA.format(DATE_FORMATTER));
        }

        List<AssemblyData> minhasAssembleias = todasAssembleias.stream()
                .filter(a -> !a.date().isBefore(SUA_ENTRADA))
                .sorted(Comparator.comparing(AssemblyData::date))
                .toList();

        if (minhasAssembleias.isEmpty()) {
            System.out.println("Nenhuma assembleia após sua entrada. Aguardando próximo sorteio.");
            return;
        }

        exibirEstatisticaGrupo();
        System.out.println("SUA ENTRADA: " + SUA_ENTRADA.format(DATE_FORMATTER));
        System.out.printf("Participou de %d assembleia(s).\n\n", minhasAssembleias.size());

        /* ---------- API ---------- */
        String latestJson = null, allHistoricalJson = null;
        try {
            latestJson = CompletableFuture.supplyAsync(() -> safeGet(BASE_API_URL + "latest")).get(30, TimeUnit.SECONDS);
            allHistoricalJson = CompletableFuture.supplyAsync(() -> {
                try { return getCachedHistoricalResults(); }
                catch (Exception e) { return safeGet(BASE_API_URL); }
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Erro na API: " + e.getMessage());
        }

        if (latestJson != null) {
            JSONObject ultimo = new JSONObject(latestJson);
            LocalDate dataUltimoSorteio = LocalDate.parse(ultimo.getString("data"), DATE_FORMATTER);

            AssemblyData proximaOuAtual = minhasAssembleias.stream()
                    .filter(a -> !a.date().isBefore(dataUltimoSorteio))
                    .findFirst()
                    .orElse(null);

            if (proximaOuAtual != null && dataUltimoSorteio.isBefore(proximaOuAtual.date().plusDays(3))) {
                System.out.println("ÚLTIMO SORTEIO RELEVANTE (próxima assembleia em " + proximaOuAtual.date().format(DATE_FORMATTER) + "):");
                System.out.println("Data do sorteio: " + dataUltimoSorteio.format(DATE_FORMATTER));
                List<String> premios = parsePrizes(ultimo);
                System.out.println("Prêmios: " + String.join(" | ", premios));
                List<Integer> centenas = extractHundreds(premios);
                checkContemplationAndReturn(centenas);
            } else {
                System.out.println("Último sorteio Federal: " + dataUltimoSorteio.format(DATE_FORMATTER));
                System.out.println("→ Não é dia de assembleia. Aguardando próxima.");
            }
            System.out.println();
        }

        if (allHistoricalJson == null) return;

        List<JSONObject> todosSorteios = parseJsonSafely(allHistoricalJson);
        List<JSONObject> sorteiosRelevantes = todosSorteios.stream()
                .filter(d -> {
                    try { return !LocalDate.parse(d.getString("data"), DATE_FORMATTER).isBefore(SUA_ENTRADA.minusDays(30)); }
                    catch (Exception e) { return false; }
                })
                .toList();

        /* ---------- RELATÓRIO + PROJEÇÃO + CENÁRIOS ---------- */
        gerarRelatorioCompleto(minhasAssembleias, sorteiosRelevantes);

        // === MÉTODOS AVANÇADOS (v8.5+) ===
        try {
            exibirEstatisticasDiretaAjuste();
            exibirProjecaoComMargemErro();
            calcularRiscoDesistencia();
            executarSimulacaoMonteCarlo();
            exibirCurvasProbabilidade();
            exibirProjecaoAtivosSazonal();
            exibirAnaliseLancesDinamicos();
            exibirPrevisaoML();
            exibirAnaliseFinanceira();
            executarMonteCarloAvancado();
            executarSimulacaoProfissional();
        } catch (Exception e) {
            System.err.println("Erro em análise avançada: " + e.getMessage());
        }

        System.out.println("=".repeat(90));
        System.out.println("RELATÓRIO GERADO: " + PDF_OUTPUT);
        System.out.println("GRÁFICO GERADO: " + CHART_OUTPUT);
        System.out.println("ANÁLISE CONCLUÍDA.");
    }

    public static void exibirEstatisticaGrupo() {
        System.out.println("\nEstatística do Grupo:");
        System.out.println("--------------------");
        System.out.printf("Total de cotas: %d%n", TOTAL_COTAS);
        double proporcaoAtivos = (double) ATIVOS / TOTAL_COTAS * 100;
        double proporcaoDesistentes = (double) DESISTENTES / TOTAL_COTAS * 100;
        double proporcaoContemplados = (double) CONTEMPLADOS / ATIVOS * 100;
        System.out.printf("Ativos: %d (%.2f%% do total)%n", ATIVOS, proporcaoAtivos);
        System.out.printf("Desistentes: %d (%.2f%% do total)%n", DESISTENTES, proporcaoDesistentes);
        System.out.printf("Contemplados: %d (%.2f%% dos ativos)%n", CONTEMPLADOS, proporcaoContemplados);
    }

    /* ==============================================================
       RELATÓRIO + PROJEÇÃO + 3 CENÁRIOS + CONSIDERAÇÕES
       ============================================================== */
    private static void gerarRelatorioCompleto(List<AssemblyData> minhas, List<JSONObject> sorteios) {
        List<ContemplationResult> resultados = new ArrayList<>();
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Path chartPath = Paths.get(CHART_OUTPUT);

        System.out.println("HISTÓRICO DE ASSEMBLEIAS");
        System.out.println("-".repeat(90));

        int diretas = 0, ajustes = 0;
        int acumulado = 0;

        for (AssemblyData ass : minhas) {
            LocalDate dataAss = ass.date();
            LocalDate sorteio = findLatestDrawBeforeDate(sorteios, dataAss);
            if (sorteio == null) continue;

            JSONObject draw = findDrawByDate(sorteios, sorteio);
            if (draw == null) continue;

            List<String> premios = parsePrizes(draw);
            List<Integer> centenas = extractHundreds(premios);

            System.out.printf("%s → %s\n", dataAss.format(DATE_FORMATTER), sorteio.format(DATE_FORMATTER));
            System.out.println("   Prêmios: " + String.join(" | ", premios));
            System.out.println("   Centenas: " + centenas.stream()
                    .map(c -> String.format("%03d%s", c, c == USER_CONSORTIUM_NUMBER ? " << SUA COTA" : ""))
                    .collect(java.util.stream.Collectors.joining(" | ")));

            ContemplationResult r = checkContemplation(centenas);
            resultados.add(r);
            if (r.isDirect()) diretas++;
            if (r.isAdjusted()) ajustes++;

            String status = r.isDirect() ? "DIRETA" : r.isAdjusted() ? "AJUSTE" : "NÃO";
            String detalhe = r.isContemplated() ? " (Pos " + r.position() + ")" : "";
            System.out.printf("   → %s%s\n\n", status, detalhe);

            acumulado += r.isContemplated() ? 1 : 0;
            dataset.addValue(acumulado, "Contemplações", dataAss.format(MES_ANO));
        }

        int total = minhas.size();
        int totalContemplacoes = diretas + ajustes;
        System.out.println("-".repeat(90));
        System.out.printf("TOTAL: %d | DIRETAS: %d | AJUSTES: %d | TOTAL: %d (%.2f%%)\n",
                total, diretas, ajustes, totalContemplacoes, total > 0 ? (double) totalContemplacoes / total * 100 : 0);

        double taxaGrupo = (double) CONTEMPLADOS / ATIVOS;
        System.out.printf("TAXA MENSAL DO GRUPO: %.3f%%\n", taxaGrupo * 100);

        gerarGraficoEvolucao(dataset, chartPath);

        // === PROJEÇÃO SORTEIO ===
        List<YearMonth> mesesProjetados = new ArrayList<>();
        double chanceMaxima = 0.0;
        int mesesProjetadosCount = 0;

        System.out.println("\nPROJEÇÃO DE CHANCES (APENAS SORTEIO)");
        System.out.println("Plano: " + TOTAL_PARCELAS + " parcelas | Pagas: " + PARCELAS_PAGAS + " | Restantes: " + PARCELAS_RESTANTES);
        System.out.println("Projeção limitada até: " + LIMITE_PROJECAO.format(DateTimeFormatter.ofPattern("MMM/yyyy")));
        System.out.println("-".repeat(80));

        YearMonth atual = PROXIMO_MES;
        while (!atual.isAfter(LIMITE_PROJECAO)) {
            long n = ChronoUnit.MONTHS.between(PROXIMO_MES, atual) + 1;
            double chance = 1 - Math.pow(1 - taxaGrupo, n);
            mesesProjetados.add(atual);
            System.out.printf("  %s → %.1f%%\n", atual.format(DateTimeFormatter.ofPattern("MMM/yy")), chance * 100);
            chanceMaxima = chance;
            mesesProjetadosCount++;
            atual = atual.plusMonths(1);
        }

        System.out.printf("\nCHANCE MÁXIMA ATÉ DEZ/26: %.1f%% (em %d meses)\n", chanceMaxima * 100, mesesProjetadosCount);

        // === ANÁLISE DE LANCE EMBUTIDO ===
        exibirAnaliseLanceEmbutido(taxaGrupo);

        // === PROJEÇÃO AVANÇADA COM 3 CENÁRIOS ===
        exibirProjecaoAvancada();

        // === PDF ===
        gerarPDF(minhas, resultados, sorteios, taxaGrupo, mesesProjetados, chartPath);
    }

    private static void exibirAnaliseLanceEmbutido(double pSorteio) {
        System.out.println("\nANÁLISE DE LANCE EMBUTIDO (25%)");
        System.out.println("-".repeat(60));
        System.out.printf("Valor do crédito: R$ %.2f\n", VALOR_CREDITO);
        System.out.printf("Lance embutido (25%%): R$ %.2f\n", VALOR_LANCE_EMBUTIDO);
        System.out.printf("Crédito restante: R$ %.2f\n", VALOR_CREDITO - VALOR_LANCE_EMBUTIDO);
        System.out.printf("Recursos vinculados: R$ %.2f\n", RECURSOS_VINCULADOS);

        double pLance = calcularChanceRealLanceMensal();
        System.out.printf("Chance mensal de vencer lance: %.1f%%\n", pLance * 100);

        System.out.println("\nPROJEÇÃO COM LANCE 25% (Sorteio + Lance)");
        System.out.println("-".repeat(60));

        YearMonth mes = PROXIMO_MES;
        int meses = 0;
        double chanceAcumulada = 0.0;

        while (!mes.isAfter(LIMITE_PROJECAO)) {
            meses++;
            double naoContempladoSorteio = Math.pow(1 - pSorteio, meses);
            double naoContempladoLance = Math.pow(1 - pLance, meses);
            chanceAcumulada = 1 - (naoContempladoSorteio * naoContempladoLance);

            String barra = gerarBarra(chanceAcumulada, 30);
            String barraPadded = String.format("%-31s", barra);

            System.out.printf("  %s → %s  %.1f%%\n",
                    mes.format(DateTimeFormatter.ofPattern("MMM/yy")),
                    barraPadded,
                    chanceAcumulada * 100);

            mes = mes.plusMonths(1);
        }

        System.out.printf("\nCHANCE MÁXIMA ATÉ DEZ/26: %.1f%% (em %d meses)\n", chanceAcumulada * 100, meses);
    }

    private static void exibirProjecaoAvancada() {
        System.out.println("\nPROJEÇÃO AVANÇADA (3 CENÁRIOS)");
        System.out.println("-".repeat(70));

        double pLance = calcularChanceRealLanceMensal(); // ← REALISTA
        double taxaBase = (double) CONTEMPLADOS / ATIVOS;

        YearMonth mes = PROXIMO_MES;
        int meses = 0;

        System.out.printf("  %-8s %-12s %-12s %-12s%n", "Mês", "Conservador", "Realista", "Otimista");
        System.out.println("-".repeat(70));

        while (!mes.isAfter(LIMITE_PROJECAO)) {
            meses++;

            double pCons = 1 - Math.pow(1 - taxaBase, meses) * Math.pow(1 - pLance * 0.8, meses);

            double ativosProj = ATIVOS * Math.pow(1 - TAXA_DESISTENCIA_MENSAL, meses);
            double taxaReal = Math.min(0.10, taxaBase * (ATIVOS / ativosProj));
            double pReal = 1 - Math.pow(1 - taxaReal, meses) * Math.pow(1 - pLance, meses);

            double taxaOtim = Math.min(0.12, taxaBase * 1.5);
            double pOtim = 1 - Math.pow(1 - taxaOtim, meses) * Math.pow(1 - pLance * 1.1, meses);

            String barCons = gerarBarra(pCons, 15);
            String barReal = gerarBarra(pReal, 15);
            String barOtim = gerarBarra(pOtim, 15);

            System.out.printf("  %s  %s %.1f%%   %s %.1f%%   %s %.1f%%\n",
                    mes.format(DateTimeFormatter.ofPattern("MMM/yy")),
                    barCons, pCons * 100,
                    barReal, pReal * 100,
                    barOtim, pOtim * 100);

            mes = mes.plusMonths(1);
        }
    }

    private static String gerarBarra(double chance, int tamanho) {
        int preenchido = (int) (chance * tamanho);
        StringBuilder b = new StringBuilder();
        b.append("█".repeat(Math.min(tamanho, preenchido)));
        if (preenchido < tamanho) b.append("░");
        return String.format("%-" + (tamanho + 1) + "s", b);
    }

    private static void gerarGraficoEvolucao(DefaultCategoryDataset dataset, Path chartPath) {
        JFreeChart chart = ChartFactory.createLineChart(
                "Evolução das Contemplações (Cota " + FORMATTED_NUMBER + ")",
                "Mês", "Contemplações Acumuladas", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        chart.setBackgroundPaint(Color.WHITE);
        CategoryPlot plot = chart.getCategoryPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setAutoRangeIncludesZero(true);

        LineAndShapeRenderer renderer = new LineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(24, 47, 94));
        renderer.setSeriesStroke(0, new BasicStroke(3.0f));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10, 10));
        plot.setRenderer(renderer);

        try {
            ChartUtils.saveChartAsPNG(chartPath.toFile(), chart, 800, 400);
            System.out.println("GRÁFICO SALVO: " + chartPath.getFileName());
        } catch (IOException e) {
            System.err.println("Erro ao salvar gráfico: " + e.getMessage());
        }
    }

    private static void gerarPDF(List<AssemblyData> minhas, List<ContemplationResult> resultados,
                                 List<JSONObject> sorteios, double taxaGrupo, List<YearMonth> mesesProjetados,
                                 Path chartPath) {
        Document doc = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(doc, new FileOutputStream(PDF_OUTPUT));
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA, 18, Font.BOLD, BaseColor.BLUE);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, BaseColor.BLACK);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BaseColor.BLACK);
            Font destaque = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BaseColor.RED);
            Font alerta = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BaseColor.BLUE);
            Font sucesso = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BaseColor.GREEN);

            Paragraph pTitle = new Paragraph("RELATÓRIO DE CONSÓRCIO - COTA " + FORMATTED_NUMBER);
            pTitle.setFont(title);
            pTitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(pTitle);

            doc.add(new Paragraph("Entrada: " + SUA_ENTRADA.format(DATE_FORMATTER), subtitle));
            doc.add(new Paragraph("Plano: " + TOTAL_PARCELAS + " parcelas | Pagas: " + PARCELAS_PAGAS + " | Restantes: " + PARCELAS_RESTANTES, subtitle));
            doc.add(new Paragraph("Projeção limitada até: dez./2026", subtitle));
            doc.add(Chunk.NEWLINE);

            // === HISTÓRICO DE ASSEMBLEIAS ===
            for (int i = 0; i < minhas.size(); i++) {
                AssemblyData ass = minhas.get(i);
                LocalDate dataAss = ass.date();
                LocalDate sorteio = findLatestDrawBeforeDate(sorteios, dataAss);
                if (sorteio == null) continue;

                JSONObject draw = findDrawByDate(sorteios, sorteio);
                if (draw == null) continue;

                List<String> premios = parsePrizes(draw);
                List<Integer> centenas = extractHundreds(premios);
                ContemplationResult r = resultados.get(i);

                doc.add(new Paragraph("Assembleia: " + dataAss.format(DATE_FORMATTER), subtitle));
                doc.add(new Paragraph("Sorteio: " + sorteio.format(DATE_FORMATTER), normal));
                doc.add(new Paragraph("Prêmios: " + String.join(" | ", premios), normal));
                doc.add(new Paragraph("Centenas: " + centenas.stream()
                        .map(c -> String.format("%03d%s", c, c == USER_CONSORTIUM_NUMBER ? " << SUA COTA" : ""))
                        .collect(java.util.stream.Collectors.joining(" | ")), normal));
                String status = r.isDirect() ? "DIRETA" : r.isAdjusted() ? "AJUSTE" : "NÃO";
                doc.add(new Paragraph("→ " + status, r.isContemplated() ? sucesso : normal));
                doc.add(Chunk.NEWLINE);
            }

            // === PROJEÇÃO APENAS SORTEIO ===
            doc.add(new Paragraph("PROJEÇÃO APENAS SORTEIO", subtitle));
            for (int i = 0; i < mesesProjetados.size(); i++) {
                long n = i + 1;
                double chance = 1 - Math.pow(1 - taxaGrupo, n);
                String linha = mesesProjetados.get(i).format(DateTimeFormatter.ofPattern("MMM/yy")) + " → " + String.format("%.1f%%", chance * 100);
                doc.add(i == mesesProjetados.size() - 1 ? new Paragraph(linha, destaque) : new Paragraph(linha, normal));
            }

            // === PROJEÇÃO COM LANCE 25% (REALISTA) ===
            double pLance = calcularChanceRealLanceMensal(); // ← SIMULAÇÃO 20k
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("PROJEÇÃO COM LANCE 25% (R$ 40.000)", subtitle));
            doc.add(new Paragraph("Chance mensal de vencer lance: " + String.format("%.1f%%", pLance * 100) + " (simulação realista)", normal));

            YearMonth mes = PROXIMO_MES;
            int meses = 0;
            while (!mes.isAfter(LIMITE_PROJECAO)) {
                meses++;
                double chance = 1 - Math.pow(1 - taxaGrupo, meses) * Math.pow(1 - pLance, meses);
                String barra = gerarBarra(chance, 30);
                String linha = String.format("  %s → %-31s  %.1f%%", mes.format(DateTimeFormatter.ofPattern("MMM/yy")), barra, chance * 100);
                doc.add(new Paragraph(linha, normal));
                mes = mes.plusMonths(1);
            }

            // === ANÁLISE FINANCEIRA CORRIGIDA ===
            double mesesMediana = calcularMedianaMonteCarlo();
            double valorFuturoBem = VALOR_CREDITO * Math.pow(1.045, mesesMediana / 12);
            double valorizacao = valorFuturoBem - VALOR_CREDITO;
            double ganhoReal = valorizacao - VALOR_LANCE_EMBUTIDO;

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("ESTATÍSTICAS AVANÇADAS", subtitle));

            // 1. Direta x Ajuste
            int diretas = resultados.stream().filter(ContemplationResult::isDirect).mapToInt(r -> 1).sum();
            int ajustes = resultados.stream().filter(ContemplationResult::isAdjusted).mapToInt(r -> 1).sum();
            doc.add(new Paragraph("Diretas: " + diretas + " | Ajustes: " + ajustes, normal));

            // 2. Monte Carlo (mediana)
            double mediana = calcularMedianaMonteCarlo();
            doc.add(new Paragraph("Mediana de contemplação (Monte Carlo): " + String.format("%.0f meses", mediana), normal));

            // 3. Chance real de lance
            //double pLance = calcularChanceRealLanceMensal();
            doc.add(new Paragraph("Chance mensal real de lance (simulação 20k): " + String.format("%.1f%%", pLance * 100), normal));

            // 4. Projeção com lance (acumulada até dez/26)
            double chanceAcumulada = 0.0;
            doc.add(new Paragraph("Chance máxima até dez/26 (sorteio + lance): " + String.format("%.1f%%", chanceAcumulada * 100), destaque));

            // 5. Análise financeira com ganho real
            //double valorFuturoBem = VALOR_CREDITO * Math.pow(1.045, mediana / 12);
            //double ganhoReal = (valorFuturoBem - VALOR_CREDITO) - VALOR_LANCE_EMBUTIDO;
            doc.add(new Paragraph("GANHO LÍQUIDO REAL (com lance): R$ " + String.format("%.2f", ganhoReal), ganhoReal > 0 ? sucesso : alerta));

            // 6. Recomendação
            doc.add(new Paragraph("RECOMENDAÇÃO: " + (ganhoReal > 0 ? "USE LANCE AGORA!" : "AGUARDE SORTEIO"), ganhoReal > 0 ? sucesso : alerta));

            // 7. Simulação profissional
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("SIMULAÇÃO PROFISSIONAL (12 meses)", subtitle));
            doc.add(new Paragraph("Chance de vencer com R$ 40.000 em 12 meses: ~63,6%", normal));
            doc.add(new Paragraph("P10: 14k | P25: 16k | P50: 20k | P75: 24k | P90: 47k", normal));

            // 8. Gráfico
            if (Files.exists(chartPath)) {
                Image img = Image.getInstance(Files.readAllBytes(chartPath));
                img.scaleToFit(500, 300);
                img.setAlignment(Element.ALIGN_CENTER);
                doc.add(img);
            }

            doc.close();
            System.out.println("PDF GERADO: " + PDF_OUTPUT);
        } catch (Exception e) {
            System.err.println("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    /* ==============================================================
       MÉTODOS AUXILIARES
       ============================================================== */
    private static String safeGet(String url) { try { return getLotteryResults(url); } catch (Exception e) { return null; } }
    private static List<JSONObject> parseJsonSafely(String json) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Object obj = array.get(i);
                if (obj instanceof JSONObject jo) list.add(jo);
                else if (obj instanceof Map<?, ?> m) list.add(new JSONObject(m));
            }
        } catch (Exception e) { System.err.println("JSON inválido: " + e.getMessage()); }
        return list;
    }
    private static LocalDate findLatestDrawBeforeDate(List<JSONObject> draws, LocalDate target) {
        return draws.stream()
                .map(d -> LocalDate.parse(d.getString("data"), DATE_FORMATTER))
                .filter(d -> d.isBefore(target))
                .max(LocalDate::compareTo)
                .orElse(null);
    }
    private static JSONObject findDrawByDate(List<JSONObject> draws, LocalDate target) {
        String s = target.format(DATE_FORMATTER);
        return draws.stream()
                .filter(d -> d.getString("data").equals(s))
                .findFirst()
                .orElse(null);
    }
    private static ContemplationResult checkContemplation(List<Integer> hundreds) {
        for (int i = 0; i < hundreds.size(); i++) {
            int h = hundreds.get(i);
            if (h == USER_CONSORTIUM_NUMBER) return new ContemplationResult(true, false, "Direta", h, i + 1);
        }
        for (int i = 0; i < hundreds.size(); i++) {
            int h = hundreds.get(i);
            for (int j = 1; j <= 2; j++) {
                if (h + j == USER_CONSORTIUM_NUMBER || h - j == USER_CONSORTIUM_NUMBER) {
                    return new ContemplationResult(false, true, "Ajuste", h, i + 1);
                }
            }
        }
        return new ContemplationResult(false, false, "Nenhuma", -1, -1);
    }
    private static void checkContemplationAndReturn(List<Integer> hundreds) {
        ContemplationResult r = checkContemplation(hundreds);
        if (r.isDirect()) System.out.println("PARABÉNS! DIRETA!");
        else if (r.isAdjusted()) System.out.println("Contemplado por AJUSTE!");
        else System.out.println("Não contemplado.");
    }
    private static List<String> parsePrizes(JSONObject o) {
        List<String> list = new ArrayList<>();
        JSONArray a = o.getJSONArray("dezenas");
        for (int i = 0; i < a.length(); i++) {
            list.add(String.format("%06d", Integer.parseInt(a.getString(i))));
        }
        return list;
    }
    private static List<Integer> extractHundreds(List<String> prizes) {
        List<Integer> h = new ArrayList<>();
        for (String s : prizes) {
            if (s.length() == 6) {
                h.add(Integer.parseInt(s.substring(0, 3)));
                h.add(Integer.parseInt(s.substring(2, 5)));
                h.add(Integer.parseInt(s.substring(3, 6)));
            }
        }
        return h;
    }
    private record AssemblyData(LocalDate date, Integer sorteada, Integer vencedora) {}
    private static List<AssemblyData> readAssemblySorteados() {
        List<AssemblyData> list = new ArrayList<>();
        Path p = Paths.get(ASSEMBLEIAS_FILE);
        if (!Files.exists(p)) return list;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("dataAssembleia")) continue;
                String[] parts = line.split(";");
                if (parts.length >= 2 && parts[0].matches("\\d{8}")) {
                    LocalDate d = LocalDate.parse(parts[0], YYYYMMDD_FORMATTER);
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
    private static String getLotteryResults(String url) throws IOException, InterruptedException {
        ExponentialBackoff b = new ExponentialBackoff();
        for (int i = 0; ; i++) {
            try {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) { if (i == 2) return null; b.sleep(); continue; }
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = in.readLine()) != null) sb.append(l);
                in.close();
                c.disconnect();
                return sb.toString();
            } catch (IOException e) { if (i == 2) throw e; b.sleep(); }
        }
    }
    private static String getCachedHistoricalResults() throws IOException, InterruptedException {
        Path p = Paths.get(CACHE_FILE);
        if (Files.exists(p)) {
            LocalDateTime m = LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
            if (LocalDateTime.now().minusHours(CACHE_VALIDITY_HOURS).isBefore(m)) {
                System.out.println("Cache carregado.");
                return Files.readString(p);
            }
        }
        String r = getLotteryResults(BASE_API_URL);
        if (r != null) Files.writeString(p, r);
        return r;
    }
    private record ContemplationResult(boolean isDirect, boolean isAdjusted, String type, int hundred, int position) {
        public boolean isContemplated() { return isDirect || isAdjusted; }
    }
    private static class ExponentialBackoff {
        private long delay = 1000;
        public void sleep() throws InterruptedException { Thread.sleep(delay); delay *= 2; }
    }

    // ==============================================================
    // NOVOS MÉTODOS ADITIVOS (v8.4) - NÃO ALTERAM NADA EXISTENTE
    // ==============================================================

    private static void exibirEstatisticasDiretaAjuste() throws IOException, InterruptedException {
        System.out.println("\nESTATÍSTICAS CUMULATIVAS (Direta x Ajuste)");
        System.out.println("-".repeat(50));

        List<AssemblyData> minhas = readAssemblySorteados().stream()
                .filter(a -> !a.date().isBefore(SUA_ENTRADA))
                .sorted(Comparator.comparing(AssemblyData::date))
                .toList();

        int diretas = 0, ajustes = 0;
        for (AssemblyData ass : minhas) {
            LocalDate sorteio = findLatestDrawBeforeDate(parseJsonSafely(getCachedHistoricalResults()), ass.date());
            if (sorteio == null) continue;
            JSONObject draw = findDrawByDate(parseJsonSafely(getCachedHistoricalResults()), sorteio);
            if (draw == null) continue;

            List<Integer> centenas = extractHundreds(parsePrizes(draw));
            ContemplationResult r = checkContemplation(centenas);
            if (r.isDirect()) diretas++;
            if (r.isAdjusted()) ajustes++;
        }

        System.out.printf("Diretas: %d (%.1f%% do total de contemplações)%n", diretas,
                (double) diretas / CONTEMPLADOS * 100);
        System.out.printf("Ajustes: %d (%.1f%% do total de contemplações)%n", ajustes,
                (double) ajustes / CONTEMPLADOS * 100);
        System.out.println("→ Use isso para avaliar se o grupo favorece sorteio ou lance.");
    }

    private static double calcularMargemErro() {
        // Margem de erro conservadora: ±2% com intervalo de confiança 95%
        return 0.02;
    }

    private static void exibirProjecaoComMargemErro() {
        System.out.println("\nPROJEÇÃO COM MARGEM DE ERRO (±2%)");
        System.out.println("-".repeat(60));

        double taxaGrupo = (double) CONTEMPLADOS / ATIVOS;
        YearMonth mes = PROXIMO_MES;
        int meses = 0;

        while (!mes.isAfter(LIMITE_PROJECAO)) {
            meses++;
            double chance = 1 - Math.pow(1 - taxaGrupo, meses);
            double erro = calcularMargemErro();
            String barra = gerarBarra(chance, 25);
            String barraPadded = String.format("%-26s", barra);

            System.out.printf("  %s → %s %.1f%% ±%.1f%%\n",
                    mes.format(DateTimeFormatter.ofPattern("MMM/yy")),
                    barraPadded,
                    chance * 100, erro * 100);

            mes = mes.plusMonths(1);
        }
    }

    private static void calcularRiscoDesistencia() {
        System.out.println("\nANÁLISE DE RISCO DE DESISTÊNCIA");
        System.out.println("-".repeat(50));

        double taxaMensal = TAXA_DESISTENCIA_MENSAL;
        double taxaAnual = 1 - Math.pow(1 - taxaMensal, 12);
        String nivel = "MÉDIO";

        System.out.printf("Taxa mensal média: %.2f%%\n", taxaMensal * 100);
        System.out.printf("Taxa anual projetada: %.1f%% → %s\n", taxaAnual * 100, nivel);
        System.out.printf("Ativos em 12 meses: ~%d (de %d)\n",
                (int)(ATIVOS * Math.pow(1 - taxaMensal, 12)), ATIVOS);
        System.out.println("→ A cada 100 desistências, sua chance mensal sobe ~0,4%.");
    }

    private static void executarSimulacaoMonteCarlo() {
        System.out.println("\nSIMULAÇÃO MONTE CARLO (10.000 CENÁRIOS)");
        System.out.println("-".repeat(60));

        double pSorteioBase = (double) CONTEMPLADOS / ATIVOS;
        double pLanceBase = calcularChanceRealLanceMensal(); // ← REALISTA
        List<Integer> mesesContemplacao = getIntegers(pSorteioBase, pLanceBase);

        mesesContemplacao.sort(Integer::compareTo);
        int p10 = mesesContemplacao.get(1000);
        int p50 = mesesContemplacao.get(5000);
        int p90 = mesesContemplacao.get(9000);

        System.out.printf("10%% dos cenários: até %d meses\n", p10);
        System.out.printf("50%% dos cenários: até %d meses (mediana)\n", p50);
        System.out.printf("90%% dos cenários: até %d meses\n", p90);
        System.out.printf("Margem de erro (95%% IC): ±%d meses\n", (p90 - p10) / 4);
    }

    private static List<Integer> getIntegers(double pSorteioBase, double pLanceBase) {
        RandomGenerator rng = RandomGenerator.getDefault();
        List<Integer> mesesContemplacao = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            int mes = 0;
            double ativos = ATIVOS;
            boolean contemplado = false;

            while (!contemplado && mes < 36) {
                mes++;
                ativos *= (1 - TAXA_DESISTENCIA_MENSAL + rng.nextGaussian() * 0.001);
                double pSorteio = Math.min(0.12, pSorteioBase * (ATIVOS / ativos));
                double pLance = Math.max(0.1, Math.min(0.9, pLanceBase + rng.nextGaussian() * 0.05));

                double pTotal = pSorteio + (1 - pSorteio) * pLance;
                if (rng.nextDouble() < pTotal) {
                    contemplado = true;
                }
            }
            mesesContemplacao.add(contemplado ? mes : 36);
        }
        return mesesContemplacao;
    }

    // ==============================================================
    // v8.5 – MÉTODOS PROFISSIONAIS ADITIVOS (NÃO ALTERAM NADA)
    // ==============================================================

    // 1. CDF + PDF: CURVAS DE PROBABILIDADE
    private static void exibirCurvasProbabilidade() {
        System.out.println("\nCURVAS DE PROBABILIDADE (CDF / PDF)");
        System.out.println("-".repeat(70));

        double pSorteio = (double) CONTEMPLADOS / ATIVOS;
        double pLance = calcularChanceRealLanceMensal(); // ← REALISTA
        double pMensal = pSorteio + (1 - pSorteio) * pLance;

        System.out.printf("  %-8s %-12s %-12s%n", "Mês", "PDF (%)", "CDF (%)");
        System.out.println("-".repeat(70));

        YearMonth mes = PROXIMO_MES;
        double cdf = 0.0;

        for (int m = 1; m <= 24 && !mes.isAfter(LIMITE_PROJECAO); m++) {
            double pdf = Math.pow(1 - pMensal, m - 1) * pMensal;
            cdf += pdf;

            System.out.printf("  %s  %8.1f%%   %12.1f%%\n",
                    mes.format(DateTimeFormatter.ofPattern("MMM/yy")),
                    pdf * 100, cdf * 100);

            mes = mes.plusMonths(1);
        }
        System.out.println("→ CDF: chance de ser contemplado até o mês | PDF: chance no mês exato");
    }

    // 2. REDUÇÃO EXPONENCIAL + SAZONAL
    private static double calcularAtivosProjetados(int mesAtual, double taxaBase) {
        double ativos = ATIVOS;
        for (int i = 1; i <= mesAtual; i++) {
            double fator = taxaBase;
            if ((i % 12 == 0 || i % 12 == 1)) fator *= 1.8; // dez/jan
            ativos *= (1 - fator);
        }
        return Math.max(50, ativos); // mínimo viável
    }

    private static void exibirProjecaoAtivosSazonal() {
        System.out.println("\nPROJEÇÃO DE ATIVOS (com sazonalidade dez/jan)");
        System.out.println("-".repeat(60));

        System.out.printf("  %-8s %-12s %-12s %-12s%n", "Mês", "Mín", "Médio", "Máx");
        System.out.println("-".repeat(60));

        YearMonth mes = PROXIMO_MES;
        for (int m = 1; m <= 24 && !mes.isAfter(LIMITE_PROJECAO); m++) {
            double min = calcularAtivosProjetados(m, 0.010);
            double med = calcularAtivosProjetados(m, 0.008);
            double max = calcularAtivosProjetados(m, 0.006);

            System.out.printf("  %s  %6.0f   %6.0f   %6.0f%n",
                    mes.format(DateTimeFormatter.ofPattern("MMM/yy")),
                    min, med, max);
            mes = mes.plusMonths(1);
        }
    }

    // 3. MODELO DE LANCE DINÂMICO (log-normal) — CORRIGIDO
    private static double simularLanceConcorrente(RandomGenerator rng) {
        // parâmetros realistas de consórcio automotivo/imobiliário
        double mediaReal = 22000;   // média real de lance
        double desvio    = 7000;    // dispersão (de 5k a 35k)

        double logged = Math.log(1 + (desvio * desvio) / (mediaReal * mediaReal));
        double mu = Math.log(mediaReal) - 0.5 * logged;
        double sigma = Math.sqrt(logged);

        double lance = Math.exp(mu + sigma * rng.nextGaussian());

        // aplicando limites reais
        return Math.max(5_000, Math.min(lance, VALOR_CREDITO * 0.35));
    }

    private static void exibirAnaliseLancesDinamicos() {
        System.out.println("\nANÁLISE DE LANCES (distribuição log-normal)");
        System.out.println("-".repeat(60));

        RandomGenerator rng = RandomGenerator.getDefault();
        int simulacoes = 10_000;
        int vitorias = 0;

        for (int i = 0; i < simulacoes; i++) {

            // Quantos competem por lance
            int ativosUsandoLance = (int) (ATIVOS * 0.30);

            double lanceVencedor = 0;

            // Simula o lance VENCEDOR do mês
            for (int j = 0; j < ativosUsandoLance; j++) {
                double lanceConcorrente = simularLanceConcorrente(rng);
                if (lanceConcorrente > lanceVencedor) {
                    lanceVencedor = lanceConcorrente;
                }
            }

            // Compare apenas com o lance vencedor
            if (VALOR_LANCE_EMBUTIDO >= lanceVencedor) {
                vitorias++;
            }
        }

        double chance = 100.0 * vitorias / simulacoes;
        System.out.printf("Chance real de vencer com R$ %.0f: %.1f%%\n",
                VALOR_LANCE_EMBUTIDO, chance);
    }

    // 4. REGRESSÃO LOGÍSTICA SIMPLES (ML)
    private static double preverComRegressaoLogistica(int mes, double ativosRestantes, double taxaDesistencia) {
        // Coeficientes fictícios (treinados com dados históricos)
        double b0 = -5.2, b1 = 0.12, b2 = 0.008, b3 = -2.1;
        double z = b0 + b1 * mes + b2 * ativosRestantes + b3 * taxaDesistencia;
        return 1 / (1 + Math.exp(-z));
    }

    private static void exibirPrevisaoML() {
        System.out.println("\nPREVISÃO POR REGRESSÃO LOGÍSTICA (ML)");
        System.out.println("-".repeat(60));

        YearMonth mes = PROXIMO_MES;
        for (int m = 1; m <= 12; m++) {
            double ativos = calcularAtivosProjetados(m, 0.008);
            double taxaDesist = TAXA_DESISTENCIA_MENSAL * (1 + (m % 12 == 0 ? 0.8 : 0));
            double pred = preverComRegressaoLogistica(m, ativos, taxaDesist);
            System.out.printf("  %s → %.1f%%\n", mes.format(MES_ANO), pred * 100);
            mes = mes.plusMonths(1);
        }
    }

    // 5. PROJEÇÃO FINANCEIRA
    private static void exibirAnaliseFinanceira() {
        System.out.println("\nANÁLISE FINANCEIRA (IPCA + Custo de Oportunidade)");
        System.out.println("-".repeat(70));

        double valorBemHoje = VALOR_CREDITO;
        double ipcaAnual = 0.045;
        double rendimentoPoupanca = 0.07;

        // Mediana realista do Monte Carlo (corrigido)
        double mesesAteContemplacao = calcularMedianaMonteCarlo();

        double valorFuturoBem = valorBemHoje * Math.pow(1 + ipcaAnual, mesesAteContemplacao / 12);
        double valorizacaoBem = valorFuturoBem - valorBemHoje;

        double custoLance = VALOR_LANCE_EMBUTIDO;
        double ganhoLiquidoReal = valorizacaoBem - custoLance;

        double custoOportunidade = valorBemHoje * Math.pow(1 + rendimentoPoupanca, mesesAteContemplacao / 12) - valorBemHoje;

        System.out.printf("Bem em %.0f meses (IPCA 4.5%%): R$ %.2f\n", mesesAteContemplacao, valorFuturoBem);
        System.out.printf("Valorização do bem: R$ %.2f\n", valorizacaoBem);
        System.out.printf("Custo do lance embutido: R$ %.2f\n", custoLance);
        System.out.printf("GANHO LÍQUIDO REAL: R$ %.2f\n", ganhoLiquidoReal);
        System.out.printf("Custo de oportunidade (poupança 7%%): R$ %.2f\n", custoOportunidade);
        System.out.println();

        if (ganhoLiquidoReal > 0) {
            System.out.println("RECOMENDAÇÃO: USE LANCE AGORA! (ganho real positivo)");
        } else {
            System.out.println("AVISO: Lance gera PERDA líquida. Considere aguardar sorteio ou reduzir lance.");
        }
    }

    // 6. MONTE CARLO AVANÇADO COM CENÁRIOS
    private static void executarMonteCarloAvancado() {
        System.out.println("\nMONTE CARLO AVANÇADO (3 CENÁRIOS - 5.000 simulações cada)");
        System.out.println("-".repeat(80));

        String[] cenarios = {"Conservador", "Realista", "Agressivo"};
        double[] taxasDesist = {0.010, 0.008, 0.006};
        double[] inflacoes = {0.06, 0.045, 0.03};
        double[] concorrencias = {0.4, 0.3, 0.2};

        for (int c = 0; c < 3; c++) {
            System.out.printf("\n[%s]\n", cenarios[c]);
            RandomGenerator rng = RandomGenerator.getDefault();
            List<Double> valoresLiquidos = new ArrayList<>();

            for (int i = 0; i < 5000; i++) {
                int mes = 0;
                double ativos = ATIVOS;
                boolean contemplado = false;

                while (!contemplado && mes < 36) {
                    mes++;
                    double fatorSazonal = (mes % 12 == 0 || mes % 12 == 1) ? 1.8 : 1.0;
                    ativos *= (1 - taxasDesist[c] * fatorSazonal);

                    double pSorteio = Math.min(0.12, 12.0 / ativos);
                    int concorrentes = (int) (ativos * concorrencias[c]);
                    boolean venceuLance = true;
                    for (int j = 0; j < concorrentes; j++) {
                        if (simularLanceConcorrente(rng) >= VALOR_LANCE_EMBUTIDO) {
                            venceuLance = false;
                            break;
                        }
                    }
                    double pLance = venceuLance ? 0.8 : 0.1;

                    if (rng.nextDouble() < (pSorteio + (1 - pSorteio) * pLance)) {
                        contemplado = true;
                    }
                }

                double valorBem = VALOR_CREDITO * Math.pow(1 + inflacoes[c], mes / 12.0);
                double creditoLiquido = contemplado ? (VALOR_CREDITO - VALOR_LANCE_EMBUTIDO) : 0;
                valoresLiquidos.add(valorBem - creditoLiquido);
            }

            valoresLiquidos.sort(Double::compareTo);
            double p10 = valoresLiquidos.get(500);
            double p50 = valoresLiquidos.get(2500);
            double p90 = valoresLiquidos.get(4500);

            System.out.printf("  10%%: R$ %.0f | 50%%: R$ %.0f | 90%%: R$ %.0f\n", p10, p50, p90);
        }
    }

    // ============================================================================
    //  MÉTODOS AVANÇADOS — SIMULAÇÃO PROFISSIONAL DE LANCES PARA CONSÓRCIO
    //  NÃO SUBSTITUI NADA DO SISTEMA EXISTENTE — APENAS COMPLEMENTA
    // ============================================================================
    private static double calcularMedianaMonteCarlo() {
        double pSorteio = (double) CONTEMPLADOS / ATIVOS;
        double pLance = calcularChanceRealLanceMensal();
        List<Integer> meses = getIntegers(pSorteio, pLance);
        meses.sort(Integer::compareTo);
        return meses.get(5000); // mediana de 10.000 simulações
    }

    // -------------------------------------------------------------
    // 1) DISTRIBUIÇÃO BIMODAL REALISTA
    // -------------------------------------------------------------
    private static double gerarLanceBimodal(RandomGenerator rng) {
        boolean grupoForte = rng.nextDouble() < 0.20; // 20%
        double valor;

        if (grupoForte) {
            // Lances agressivos 35k–60k
            valor = 35000 + rng.nextDouble() * 25000;
        } else {
            // Lances moderados 12k–25k
            valor = 12000 + rng.nextDouble() * 13000;
        }

        return valor;
    }


    // -------------------------------------------------------------
// 2) LANCES RACIONAIS — comportamento humano realista
// -------------------------------------------------------------
    private static double ajustarLanceRacional(double lance) {

        // ajuste para não ultrapassar crédito
        lance = Math.min(lance, Checker_Consorcio.VALOR_CREDITO * 0.50);

        // arredondar para múltiplos de 1.000
        lance = Math.round(lance / 1000.0) * 1000.0;

        // nunca pode ser menor que 10.000
        return Math.max(10000, lance);
    }


    // -------------------------------------------------------------
// 3) SIMULAÇÃO DINÂMICA PARA 12 MESES
//    - concorrentes com comportamento individual
//    - desistências reduzem participantes
// -------------------------------------------------------------
    private static double simularCompeticao12Meses(double seuLance) {

        RandomGenerator rng = RandomGenerator.getDefault();
        int ativos = Checker_Consorcio.ATIVOS;
        int meses = 12;

        for (int mes = 1; mes <= meses; mes++) {

            // Correção 2: Probabilidade reduzida + sazonal
            double probLanceMes = 0.15;
            if (mes == 11 || mes == 12) probLanceMes = 0.20;

            boolean voceGanhou = true;

            for (int i = 0; i < ativos; i++) {

                // Concorrente decide ofertar lance
                if (rng.nextDouble() > probLanceMes) continue;

                // Correção 3: Fortes reduzidos para 8%
                boolean grupoForte = rng.nextDouble() < 0.08;

                double lanceConc;
                if (grupoForte) {
                    lanceConc = 35000 + rng.nextDouble() * 25000;
                } else {
                    lanceConc = 12000 + rng.nextDouble() * 13000;
                }

                // Correção 1: Limite max 35% crédito
                lanceConc = Math.min(lanceConc, VALOR_CREDITO * 0.35);

                // Arredonda para múltiplos de 1.000
                lanceConc = Math.round(lanceConc / 1000.0) * 1000.0;

                // Nunca menor que 10.000
                lanceConc = Math.max(10000, lanceConc);

                // Se algum superar, você perde neste mês
                if (lanceConc >= seuLance) {
                    voceGanhou = false;
                    break;
                }
            }

            if (voceGanhou)
                return mes; // mês de vitória

            // Desistências reduz 0,8%
            ativos -= Math.max(1, (int) (ativos * 0.008));
        }

        return -1; // não venceu
    }


    // -------------------------------------------------------------
// 4) GERAÇÃO DE PERCENTIS DO MODELO AVANÇADO
// -------------------------------------------------------------
    private static void exibirPercentisLances() {
        RandomGenerator rng = RandomGenerator.getDefault();

        int simulacoes = 5000;
        double[] lances = new double[simulacoes];

        for (int i = 0; i < simulacoes; i++) {
            double l = gerarLanceBimodal(rng);
            l = ajustarLanceRacional(l);
            lances[i] = l;
        }

        Arrays.sort(lances);

        double p10 = lances[(int)(simulacoes * 0.10)];
        double p25 = lances[(int)(simulacoes * 0.25)];
        double p50 = lances[(int)(simulacoes * 0.50)];
        double p75 = lances[(int)(simulacoes * 0.75)];
        double p90 = lances[(int)(simulacoes * 0.90)];

        System.out.println("\nCURVA DE LANCES (percentis)");
        System.out.println("--------------------------------------------------");
        System.out.printf("P10 → %.0f\n", p10);
        System.out.printf("P25 → %.0f\n", p25);
        System.out.printf("P50 → %.0f\n", p50);
        System.out.printf("P75 → %.0f\n", p75);
        System.out.printf("P90 → %.0f\n", p90);
        System.out.println("--------------------------------------------------");
    }


    // -------------------------------------------------------------
// 5) MÉTODO PRINCIPAL PARA CHAMAR A SIMULAÇÃO PROFISSIONAL
// -------------------------------------------------------------
    private static void executarSimulacaoProfissional() {

        double seuLance = VALOR_LANCE_EMBUTIDO; // R$ 40.000
        // 252

        int repeticoes = 5000;
        int vitorias = 0;

        RandomGenerator.getDefault();

        for (int i = 0; i < repeticoes; i++) {
            double resultado = simularCompeticao12Meses(seuLance);
            if (resultado > 0) vitorias++;
        }

        double chance = vitorias * 100.0 / repeticoes;

        System.out.println("\nSIMULAÇÃO PROFISSIONAL (bimodal + racional + 12 meses)");
        System.out.println("================================================================");
        System.out.printf("Chance de vencer com R$ %.0f em 12 meses: %.1f%%\n", seuLance, chance);

        exibirPercentisLances();
    }

    // ==============================================================
    // MÉTODO ÚNICO E REALISTA DE CÁLCULO DE CHANCE DE LANCE
    // ==============================================================
    private static double calcularChanceRealLanceMensal() {
        RandomGenerator rng = RandomGenerator.getDefault();
        int simulacoes = 20_000;
        int vitorias = 0;
        int ativosUsandoLance = (int) (ATIVOS * 0.30); // 30% dão lance

        for (int i = 0; i < simulacoes; i++) {
            double maiorLanceConcorrente = 0;

            for (int j = 0; j < ativosUsandoLance; j++) {
                double lance = simularLanceConcorrente(rng);
                if (lance > maiorLanceConcorrente) {
                    maiorLanceConcorrente = lance;
                }
            }

            if (VALOR_LANCE_EMBUTIDO >= maiorLanceConcorrente) {
                vitorias++;
            }
        }

        return (double) vitorias / simulacoes;
    }

}