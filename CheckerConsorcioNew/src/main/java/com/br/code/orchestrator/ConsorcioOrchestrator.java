package com.br.code.orchestrator;

import com.br.code.report.ReportPrinter;
import com.br.code.service.ApiService;
import com.br.code.service.AssemblyService;
import com.br.code.service.CalculationService;
import com.br.code.simulation.MonteCarloSimulator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConsorcioOrchestrator {
    private final ApiService apiService = new ApiService();
    private final AssemblyService assemblyService = new AssemblyService();
    private final CalculationService calcService = new CalculationService();
    private final MonteCarloSimulator simulator = new MonteCarloSimulator(calcService);
    private final ReportPrinter printer = new ReportPrinter(calcService);

    public void executar() {
        try {
            printer.imprimirCabecalho();
            var minhas = assemblyService.getMinhasAssembleias();
            if (minhas.isEmpty()) return;

            var latest = CompletableFuture.supplyAsync(apiService::fetchLatest).get(30, TimeUnit.SECONDS);
            var historical = CompletableFuture.supplyAsync(apiService::fetchHistorical).get(30, TimeUnit.SECONDS);

            printer.imprimirUltimoSorteio(latest, calcService);
            printer.imprimirRelatorioCompleto(minhas, historical, calcService);
            printer.imprimirAnalisesAdicionais(calcService, simulator);
            printer.imprimirRodape();
        } catch (Exception e) {
            System.err.println("Erro crítico: " + e.getMessage());
        }
    }
}