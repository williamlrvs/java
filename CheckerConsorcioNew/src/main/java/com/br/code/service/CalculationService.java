package com.br.code.service;

import com.br.code.config.ConsorcioConfig;
import com.br.code.model.ContemplationResult;
import com.br.code.util.MathUtils;
import java.util.ArrayList;
import java.util.List;

public class CalculationService {
    public double calculateTaxaGrupo() {
        return (double) ConsorcioConfig.getContemplados() / ConsorcioConfig.getAtivos();
    }

    public double calculateChanceLanceMensal() {
        double lance = ConsorcioConfig.getValorCredito() * ConsorcioConfig.getPercentualLanceEmbutido();
        double z = (lance - ConsorcioConfig.getMediaLanceVencedorEstimada()) / ConsorcioConfig.getDesvioPadraoLances();
        return Math.max(0.05, Math.min(0.95, MathUtils.normalCDF(z)));
    }

    public ContemplationResult checkContemplation(List<Integer> hundreds) {
        int user = ConsorcioConfig.getUserConsortiumNumber();
        for (int i = 0; i < hundreds.size(); i++) {
            int h = hundreds.get(i);
            if (h == user) return new ContemplationResult(true, false, "Direta", h, i + 1);
        }
        for (int i = 0; i < hundreds.size(); i++) {
            int h = hundreds.get(i);
            for (int j = 1; j <= 2; j++) {
                if (h + j == user || h - j == user) {
                    return new ContemplationResult(false, true, "Ajuste", h, i + 1);
                }
            }
        }
        return new ContemplationResult(false, false, "Nenhuma", -1, -1);
    }

    public List<Integer> extractHundreds(List<String> prizes) {
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

    public double calcularAtivosProjetados(int mesAtual, double taxaBase) {
        double ativos = ConsorcioConfig.getAtivos();
        for (int i = 1; i <= mesAtual; i++) {
            double fator = taxaBase;
            if (i % 12 == 0 || i % 12 == 1) fator *= 1.8;
            ativos *= (1 - fator);
        }
        return Math.max(50, ativos);
    }

    public double preverComRegressaoLogistica(int mes, double ativosRestantes, double taxaDesistencia) {
        double b0 = -5.2, b1 = 0.12, b2 = 0.008, b3 = -2.1;
        double z = b0 + b1 * mes + b2 * ativosRestantes + b3 * taxaDesistencia;
        return 1 / (1 + Math.exp(-z));
    }
}