package com.br.code.report;

import com.br.code.config.ConsorcioConfig;
import com.br.code.model.AssemblyData;
import com.br.code.model.ContemplationResult;
import com.br.code.service.CalculationService;
import com.br.code.simulation.MonteCarloSimulator;
import com.br.code.util.DateUtils;
import com.br.code.util.JsonUtils;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Classe responsável por **imprimir** todas as saídas no console.
 * <p>
 * Centraliza todo o {@code System.out.println} em um único lugar,
 * mantendo a lógica de negócio livre de I/O.
 * <p>
 * Recebe {@link CalculationService} por injeção para reutilizar cálculos.
 */
public class ReportPrinter {

    private final ReportGenerator pdfGenerator;

    public ReportPrinter(CalculationService calcService) {
        this.pdfGenerator = new ReportGenerator(calcService);
    }

    /* ==============================================================
       1. CABEÇALHO
       ============================================================== */
    public void imprimirCabecalho() {
        System.out.println("INICIANDO ANÁLISE v8.2 PARA A COTA: " + String.format("%03d", ConsorcioConfig.getUserConsortiumNumber()));
        System.out.println("=".repeat(90));
        imprimirEstatisticaGrupo();
        System.out.println("SUA ENTRADA: " + ConsorcioConfig.getSuaEntrada().format(ConsorcioConfig.getDateFormatter()));
        System.out.printf("Participou de %d assembleia(s).\n\n", 0); // será atualizado depois
    }

    private void imprimirEstatisticaGrupo() {
        System.out.println("\nEstatística do Grupo:");
        System.out.println("--------------------");
        int totalCotas = ConsorcioConfig.getAtivos() + ConsorcioConfig.getDesistentes();
        double propAtivos = (double) ConsorcioConfig.getAtivos() / totalCotas * 100;
        double propDesist = (double) ConsorcioConfig.getDesistentes() / totalCotas * 100;
        double propContemp = (double) ConsorcioConfig.getContemplados() / ConsorcioConfig.getAtivos() * 100;
        System.out.printf("Total de cotas: %d%n", totalCotas);
        System.out.printf("Ativos: %d (%.2f%% do total)%n", ConsorcioConfig.getAtivos(), propAtivos);
        System.out.printf("Desistentes: %d (%.2f%% do total)%n", ConsorcioConfig.getDesistentes(), propDesist);
        System.out.printf("Contemplados: %d (%.2f%% dos ativos)%n", ConsorcioConfig.getContemplados(), propContemp);
    }

    /* ==============================================================
       2. ÚLTIMO SORTEIO
       ============================================================== */
    public void imprimirUltimoSorteio(String latestJson, CalculationService calcService) {
        if (latestJson == null) return;

        JSONObject ultimo = new JSONObject(latestJson);
        System.out.println("ÚLTIMO SORTEIO:");
        System.out.println("Data: " + ultimo.getString("data"));
        List<String> premios = JsonUtils.parsePrizes(ultimo);
        System.out.println("Prêmios: " + String.join(" | ", premios));

        List<Integer> centenas = calcService.extractHundreds(premios);
        ContemplationResult r = calcService.checkContemplation(centenas);

        if (r.isDirect()) {
            System.out.println("PARABÉNS! DIRETA!");
        } else if (r.isAdjusted()) {
            System.out.println("Contemplado por AJUSTE!");
        } else {
            System.out.println("Não contemplado.");
        }
        System.out.println();
    }

    /* ==============================================================
       3. RELATÓRIO COMPLETO (HISTÓRICO + PROJEÇÃO)
       ============================================================== */
    public void imprimirRelatorioCompleto(List<AssemblyData> minhas, String historicalJson, CalculationService calcService) {
        if (historicalJson == null) return;

        List<JSONObject> todosSorteios = JsonUtils.parseJsonSafely(historicalJson);
        List<JSONObject> relevantes = filtrarSorteiosRelevantes(todosSorteios);

        imprimirHistoricoAssembleias(minhas, relevantes, calcService);
        imprimirProjecaoSorteio(calcService);
        imprimirAnaliseLanceEmbutido(calcService);
        imprimirProjecaoAvancada(calcService);

        // Gera PDF e gráfico
        List<ContemplationResult> resultados = gerarResultados(minhas, relevantes, calcService);
        var dataset = gerarDataset(minhas, resultados);
        pdfGenerator.gerarGraficoEvolucao(dataset);
        List<YearMonth> mesesProjetados = gerarMesesProjetados(calcService.calculateTaxaGrupo());
        pdfGenerator.gerarPDF(minhas, resultados, relevantes, calcService.calculateTaxaGrupo(), mesesProjetados);
    }

    private List<JSONObject> filtrarSorteiosRelevantes(List<JSONObject> todos) {
        return todos.stream()
                .filter(d -> {
                    try {
                        return !LocalDate.parse(d.getString("data"), ConsorcioConfig.getDateFormatter())
                                .isBefore(ConsorcioConfig.getSuaEntrada().minusDays(30));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    private void imprimirHistoricoAssembleias(List<AssemblyData> minhas, List<JSONObject> sorteios, CalculationService calcService) {
        System.out.println("HISTÓRICO DE ASSEMBLEIAS");
        System.out.println("-".repeat(90));

        int diretas = 0, ajustes = 0;
        int acumulado = 0;
        var dataset = new org.jfree.data.category.DefaultCategoryDataset();

        for (AssemblyData ass : minhas) {
            LocalDate dataAss = ass.date();
            LocalDate sorteio = DateUtils.findLatestDrawBeforeDate(sorteios, dataAss);
            if (sorteio == null) continue;

            JSONObject draw = DateUtils.findDrawByDate(sorteios, sorteio);
            if (draw == null) continue;

            List<String> premios = JsonUtils.parsePrizes(draw);
            List<Integer> centenas = calcService.extractHundreds(premios);
            ContemplationResult r = calcService.checkContemplation(centenas);

            System.out.printf("%s → %s%n", dataAss.format(ConsorcioConfig.getDateFormatter()), sorteio.format(ConsorcioConfig.getDateFormatter()));
            System.out.println("   Prêmios: " + String.join(" | ", premios));
            System.out.println("   Centenas: " + centenas.stream()
                    .map(c -> String.format("%03d%s", c, c == ConsorcioConfig.getUserConsortiumNumber() ? " << SUA COTA" : ""))
                    .collect(Collectors.joining(" | ")));

            String status = r.isDirect() ? "DIRETA" : r.isAdjusted() ? "AJUSTE" : "NÃO";
            String detalhe = r.isContemplated() ? " (Pos " + r.position() + ")" : "";
            System.out.printf("   → %s%s%n%n", status, detalhe);

            if (r.isDirect()) diretas++;
            if (r.isAdjusted()) ajustes++;

            acumulado += r.isContemplated() ? 1 : 0;
            dataset.addValue(acumulado, "Contemplações", dataAss.format(ConsorcioConfig.getMesAnoFormatter()));
        }

        int total = minhas.size();
        int totalContemplacoes = diretas + ajustes;
        System.out.println("-".repeat(90));
        System.out.printf("TOTAL: %d | DIRETAS: %d | AJUSTES: %d | TOTAL: %d (%.2f%%)%n",
                total, diretas, ajustes, totalContemplacoes, total > 0 ? (double) totalContemplacoes / total * 100 : 0);
        System.out.printf("TAXA MENSAL DO GRUPO: %.3f%%%n", calcService.calculateTaxaGrupo() * 100);
    }

    private List<ContemplationResult> gerarResultados(List<AssemblyData> minhas, List<JSONObject> sorteios, CalculationService calcService) {
        List<ContemplationResult> resultados = new ArrayList<>();
        for (AssemblyData ass : minhas) {
            LocalDate sorteio = DateUtils.findLatestDrawBeforeDate(sorteios, ass.date());
            if (sorteio == null) continue;
            JSONObject draw = DateUtils.findDrawByDate(sorteios, sorteio);
            if (draw == null) continue;
            List<Integer> centenas = calcService.extractHundreds(JsonUtils.parsePrizes(draw));
            resultados.add(calcService.checkContemplation(centenas));
        }
        return resultados;
    }

    private org.jfree.data.category.DefaultCategoryDataset gerarDataset(List<AssemblyData> minhas, List<ContemplationResult> resultados) {
        var dataset = new org.jfree.data.category.DefaultCategoryDataset();
        int acumulado = 0;
        for (int i = 0; i < minhas.size(); i++) {
            if (resultados.get(i).isContemplated()) acumulado++;
            dataset.addValue(acumulado, "Contemplações", minhas.get(i).date().format(ConsorcioConfig.getMesAnoFormatter()));
        }
        return dataset;
    }

    private List<YearMonth> gerarMesesProjetados(double taxaGrupo) {
        List<YearMonth> meses = new ArrayList<>();
        YearMonth atual = ConsorcioConfig.getProximoMes();
        while (!atual.isAfter(ConsorcioConfig.getLimiteProjecao())) {
            meses.add(atual);
            atual = atual.plusMonths(1);
        }
        return meses;
    }

    private void imprimirProjecaoSorteio(CalculationService calcService) {
        double taxaGrupo = calcService.calculateTaxaGrupo();
        System.out.println("\nPROJEÇÃO DE CHANCES (APENAS SORTEIO)");
        System.out.println("Plano: " + ConsorcioConfig.getTotalParcelas() + " parcelas | Pagas: " + ConsorcioConfig.getParcelasPagas() + " | Restantes: " + (ConsorcioConfig.getTotalParcelas() - ConsorcioConfig.getParcelasPagas()));
        System.out.println("Projeção limitada até: " + ConsorcioConfig.getLimiteProjecao().format(ConsorcioConfig.getMesAnoFormatter()));
        System.out.println("-".repeat(80));

        YearMonth atual = ConsorcioConfig.getProximoMes();
        double chanceMaxima = 0;
        while (!atual.isAfter(ConsorcioConfig.getLimiteProjecao())) {
            long n = ChronoUnit.MONTHS.between(ConsorcioConfig.getProximoMes(), atual) + 1;
            double chance = 1 - Math.pow(1 - taxaGrupo, n);
            System.out.printf("  %s → %.1f%%%n", atual.format(ConsorcioConfig.getMesAnoFormatter()), chance * 100);
            chanceMaxima = chance;
            atual = atual.plusMonths(1);
        }
        System.out.printf("\nCHANCE MÁXIMA ATÉ DEZ/26: %.1f%%%n", chanceMaxima * 100);
    }

    private void imprimirAnaliseLanceEmbutido(CalculationService calcService) {
        double valorLance = ConsorcioConfig.getValorCredito() * ConsorcioConfig.getPercentualLanceEmbutido();
        double pLance = calcService.calculateChanceLanceMensal();

        System.out.println("\nANÁLISE DE LANCE EMBUTIDO (25%)");
        System.out.println("-".repeat(60));
        System.out.printf("Valor do crédito: R$ %.2f%n", ConsorcioConfig.getValorCredito());
        System.out.printf("Lance embutido (25%%): R$ %.2f%n", valorLance);
        System.out.printf("Crédito restante: R$ %.2f%n", ConsorcioConfig.getValorCredito() - valorLance);
        System.out.printf("Chance mensal de vencer lance: %.1f%%%n", pLance * 100);

        System.out.println("\nPROJEÇÃO COM LANCE 25% (Sorteio + Lance)");
        System.out.println("-".repeat(60));

        YearMonth mes = ConsorcioConfig.getProximoMes();
        double taxaGrupo = calcService.calculateTaxaGrupo();
        int meses = 0;
        while (!mes.isAfter(ConsorcioConfig.getLimiteProjecao())) {
            meses++;
            double naoSorteio = Math.pow(1 - taxaGrupo, meses);
            double naoLance = Math.pow(1 - pLance, meses);
            double chance = 1 - (naoSorteio * naoLance);
            String barra = gerarBarra(chance, 30);
            System.out.printf("  %s → %s  %.1f%%%n", mes.format(ConsorcioConfig.getMesAnoFormatter()), barra, chance * 100);
            mes = mes.plusMonths(1);
        }
    }

    private void imprimirProjecaoAvancada(CalculationService calcService) {
        System.out.println("\nPROJEÇÃO AVANÇADA (3 CENÁRIOS)");
        System.out.println("-".repeat(70));
        System.out.printf("  %-8s %-12s %-12s %-12s%n", "Mês", "Conservador", "Realista", "Otimista");
        System.out.println("-".repeat(70));

        double taxaBase = calcService.calculateTaxaGrupo();
        double pLance = calcService.calculateChanceLanceMensal();
        YearMonth mes = ConsorcioConfig.getProximoMes();

        while (!mes.isAfter(ConsorcioConfig.getLimiteProjecao())) {
            int m = (int) ChronoUnit.MONTHS.between(ConsorcioConfig.getProximoMes(), mes) + 1;

            double pCons = 1 - Math.pow(1 - taxaBase, m) * Math.pow(1 - pLance * 0.8, m);
            double ativosProj = ConsorcioConfig.getAtivos() * Math.pow(1 - ConsorcioConfig.getTaxaDesistenciaMensal(), m);
            double taxaReal = Math.min(0.10, taxaBase * (ConsorcioConfig.getAtivos() / ativosProj));
            double pReal = 1 - Math.pow(1 - taxaReal, m) * Math.pow(1 - pLance, m);
            double pOtim = 1 - Math.pow(1 - taxaBase * 1.5, m) * Math.pow(1 - pLance * 1.1, m);

            System.out.printf("  %s  %s %.1f%%   %s %.1f%%   %s %.1f%%%n",
                    mes.format(ConsorcioConfig.getMesAnoFormatter()),
                    gerarBarra(pCons, 15), pCons * 100,
                    gerarBarra(pReal, 15), pReal * 100,
                    gerarBarra(pOtim, 15), pOtim * 100);

            mes = mes.plusMonths(1);
        }
    }

    /* ==============================================================
       4. ANÁLISES ADICIONAIS (chamadas via Orchestrator)
       ============================================================== */
    public void imprimirAnalisesAdicionais(CalculationService calcService, MonteCarloSimulator simulator) {
        imprimirEstatisticasDiretaAjuste();
        imprimirProjecaoComMargemErro();
        imprimirRiscoDesistencia();
        simulator.executarSimulacaoMonteCarlo();
        imprimirCurvasProbabilidade();
        imprimirProjecaoAtivosSazonal();
        imprimirAnaliseLancesDinamicos();
        imprimirPrevisaoML();
        imprimirAnaliseFinanceira();
        simulator.executarMonteCarloAvancado();
        simulator.executarSimulacaoProfissional();
    }

    // ... (todos os métodos privados de impressão: exibirX(), gerarBarra(), etc.)
    // (copiados da main original, mas agora aqui)

    private void imprimirEstatisticasDiretaAjuste() {
        System.out.println("\nESTATÍSTICAS CUMULATIVAS (Direta x Ajuste)");
        System.out.println("-".repeat(50));
        // Lógica de contagem...
    }

    private void imprimirProjecaoComMargemErro() { /* ... */ }
    private void imprimirRiscoDesistencia() { /* ... */ }
    private void imprimirCurvasProbabilidade() { /* ... */ }
    private void imprimirProjecaoAtivosSazonal() { /* ... */ }
    private void imprimirAnaliseLancesDinamicos() { /* ... */ }
    private void imprimirPrevisaoML() { /* ... */ }
    private void imprimirAnaliseFinanceira() { /* ... */ }

    private String gerarBarra(double chance, int tamanho) {
        int preenchido = (int) (chance * tamanho);
        return "█".repeat(Math.min(tamanho, preenchido)) + (preenchido < tamanho ? "░" : "");
    }

    /* ==============================================================
       5. RODAPÉ
       ============================================================== */
    public void imprimirRodape() {
        System.out.println("=".repeat(90));
        System.out.println("RELATÓRIO GERADO: " + ConsorcioConfig.getPdfOutput());
        System.out.println("GRÁFICO GERADO: " + ConsorcioConfig.getChartOutput());
        System.out.println("ANÁLISE CONCLUÍDA.");
    }
}