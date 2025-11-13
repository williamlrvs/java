package com.br.code.simulation;

import com.br.code.config.ConsorcioConfig;
import com.br.code.service.CalculationService;
import com.br.code.util.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Classe responsável por todas as simulações Monte Carlo do consórcio.
 * <p>
 * Recebe {@link CalculationService} por injeção de dependência para
 * reutilizar cálculos de taxa de grupo, chance de lance, etc.
 */
public class MonteCarloSimulator {

    private final CalculationService calcService;
    private final Random rng = new Random();

    public MonteCarloSimulator(CalculationService calcService) {
        this.calcService = calcService;
    }

    /* --------------------------------------------------------------
       1. Simulação básica (10.000 cenários)
       -------------------------------------------------------------- */
    public void executarSimulacaoMonteCarlo() {
        System.out.println("\nSIMULAÇÃO MONTE CARLO (10.000 CENÁRIOS)");
        System.out.println("-".repeat(60));

        double pSorteioBase = calcService.calculateTaxaGrupo();
        double pLanceBase   = calcService.calculateChanceLanceMensal();

        List<Integer> mesesContemplacao = simulate(pSorteioBase, pLanceBase);

        Collections.sort(mesesContemplacao);
        int p10 = mesesContemplacao.get(1_000);
        int p50 = mesesContemplacao.get(5_000);
        int p90 = mesesContemplacao.get(9_000);

        System.out.printf("10%% dos cenários: até %d meses%n", p10);
        System.out.printf("50%% dos cenários: até %d meses (mediana)%n", p50);
        System.out.printf("90%% dos cenários: até %d meses%n", p90);
        System.out.printf("Margem de erro (95%% IC): ±%d meses%n", (p90 - p10) / 4);
    }

    private List<Integer> simulate(double pSorteioBase, double pLanceBase) {
        List<Integer> mesesContemplacao = new ArrayList<>();

        for (int i = 0; i < 10000; i++) {
            int mes = 0;
            double ativos = ConsorcioConfig.getAtivos();
            boolean contemplado = false;

            while (!contemplado && mes < 36) {
                mes++;
                // variação aleatória na taxa de desistência
                ativos *= (1 - ConsorcioConfig.getTaxaDesistenciaMensal() + rng.nextGaussian() * 0.001);
                double pSorteio = Math.min(0.12, pSorteioBase * (ConsorcioConfig.getAtivos() / ativos));
                double pLance   = Math.max(0.3, Math.min(0.9, pLanceBase + rng.nextGaussian() * 0.1));

                double pTotal = pSorteio + (1 - pSorteio) * pLance;
                if (rng.nextDouble() < pTotal) {
                    contemplado = true;
                }
            }
            mesesContemplacao.add(contemplado ? mes : 36);
        }
        return mesesContemplacao;
    }

    /* --------------------------------------------------------------
       2. Monte Carlo Avançado (3 cenários – 5.000 simulações cada)
       -------------------------------------------------------------- */
    public void executarMonteCarloAvancado() {
        System.out.println("\nMONTE CARLO AVANÇADO (3 CENÁRIOS - 5.000 simulações cada)");
        System.out.println("-".repeat(80));

        String[] cenarios   = {"Conservador", "Realista", "Agressivo"};
        double[] taxasDesist = {0.010, 0.008, 0.006};
        double[] inflacoes   = {0.06, 0.045, 0.03};
        double[] concorrencias = {0.4, 0.3, 0.2};

        for (int c = 0; c < 3; c++) {
            System.out.printf("\n[%s]\n", cenarios[c]);
            List<Double> valoresLiquidos = new ArrayList<>();

            for (int i = 0; i < 5_000; i++) {
                int mes = 0;
                double ativos = ConsorcioConfig.getAtivos();
                boolean contemplado = false;

                while (!contemplado && mes < 36) {
                    mes++;
                    double fatorSazonal = (mes % 12 == 0 || mes % 12 == 1) ? 1.8 : 1.0;
                    ativos *= (1 - taxasDesist[c] * fatorSazonal);

                    double pSorteio = Math.min(0.12, 12.0 / ativos);
                    int concorrentes = (int) (ativos * concorrencias[c]);

                    boolean venceuLance = true;
                    for (int j = 0; j < concorrentes; j++) {
                        if (MathUtils.simularLanceConcorrente(rng) >=
                                ConsorcioConfig.getValorCredito() * ConsorcioConfig.getPercentualLanceEmbutido()) {
                            venceuLance = false;
                            break;
                        }
                    }
                    double pLance = venceuLance ? 0.8 : 0.1;

                    if (rng.nextDouble() < (pSorteio + (1 - pSorteio) * pLance)) {
                        contemplado = true;
                    }
                }

                double valorBem = ConsorcioConfig.getValorCredito() *
                        Math.pow(1 + inflacoes[c], mes / 12.0);
                double creditoLiquido = contemplado
                        ? (ConsorcioConfig.getValorCredito() - ConsorcioConfig.getValorCredito() *
                        ConsorcioConfig.getPercentualLanceEmbutido())
                        : 0;
                valoresLiquidos.add(valorBem - creditoLiquido);
            }

            Collections.sort(valoresLiquidos);
            double p10 = valoresLiquidos.get(500);
            double p50 = valoresLiquidos.get(2_500);
            double p90 = valoresLiquidos.get(4_500);

            System.out.printf("  10%%: R$ %.0f | 50%%: R$ %.0f | 90%%: R$ %.0f%n",
                    p10, p50, p90);
        }
    }

    /* --------------------------------------------------------------
       3. Simulação Profissional (bimodal + racional + 12 meses)
       -------------------------------------------------------------- */
    public void executarSimulacaoProfissional() {
        double seuLance = ConsorcioConfig.getValorCredito() *
                ConsorcioConfig.getPercentualLanceEmbutido();

        int repeticoes = 5_000;
        int vitorias = 0;

        for (int i = 0; i < repeticoes; i++) {
            double mesVitoria = simularCompeticao12Meses(seuLance);
            if (mesVitoria > 0) vitorias++;
        }

        double chance = vitorias * 100.0 / repeticoes;

        System.out.println("\nSIMULAÇÃO PROFISSIONAL (bimodal + racional + 12 meses)");
        System.out.println("================================================================");
        System.out.printf("Chance de vencer com R$ %.0f em 12 meses: %.1f%%\n",
                seuLance, chance);

        exibirPercentisLances();
    }

    private double simularCompeticao12Meses(double seuLance) {
        int ativos = ConsorcioConfig.getAtivos();
        for (int mes = 1; mes <= 12; mes++) {
            double probLanceMes = (mes == 11 || mes == 12) ? 0.20 : 0.15;
            boolean voceGanhou = true;

            for (int i = 0; i < ativos; i++) {
                if (rng.nextDouble() > probLanceMes) continue;

                double lanceConc = MathUtils.gerarLanceBimodal(rng);
                lanceConc = MathUtils.ajustarLanceRacional(lanceConc);

                if (lanceConc >= seuLance) {
                    voceGanhou = false;
                    break;
                }
            }

            if (voceGanhou) return mes;

            // redução de ativos por desistência
            ativos -= Math.max(1, (int) (ativos * 0.008));
        }
        return -1; // não venceu nos 12 meses
    }

    private void exibirPercentisLances() {
        int simulacoes = 5_000;
        List<Double> lances = new ArrayList<>();

        for (int i = 0; i < simulacoes; i++) {
            double l = MathUtils.gerarLanceBimodal(rng);
            l = MathUtils.ajustarLanceRacional(l);
            lances.add(l);
        }

        Collections.sort(lances);
        double p10 = lances.get((int) (simulacoes * 0.10));
        double p25 = lances.get((int) (simulacoes * 0.25));
        double p50 = lances.get((int) (simulacoes * 0.50));
        double p75 = lances.get((int) (simulacoes * 0.75));
        double p90 = lances.get((int) (simulacoes * 0.90));

        System.out.println("\nCURVA DE LANCES (percentis)");
        System.out.println("--------------------------------------------------");
        System.out.printf("P10 → %.0f%n", p10);
        System.out.printf("P25 → %.0f%n", p25);
        System.out.printf("P50 → %.0f%n", p50);
        System.out.printf("P75 → %.0f%n", p75);
        System.out.printf("P90 → %.0f%n", p90);
        System.out.println("--------------------------------------------------");
    }
}