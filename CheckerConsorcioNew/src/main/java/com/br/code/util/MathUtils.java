package com.br.code.util;

import com.br.code.config.ConsorcioConfig;
import java.util.Random;

public class MathUtils {
    public static double normalCDF(double z) {
        if (z > 3) return 0.99;
        if (z < -3) return 0.01;
        double t = 1 / (1 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2);
        double prob = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1 - prob : prob;
    }

    public static double simularLanceConcorrente(Random rng) {
        double mediaReal = 22000;
        double desvio = 7000;
        double logged = Math.log(1 + (desvio * desvio) / (mediaReal * mediaReal));
        double mu = Math.log(mediaReal) - 0.5 * logged;
        double sigma = Math.sqrt(logged);
        double lance = Math.exp(mu + sigma * rng.nextGaussian());
        return Math.max(5_000, Math.min(lance, ConsorcioConfig.getValorCredito() * 0.35));
    }

    public static double gerarLanceBimodal(Random rng) {
        boolean grupoForte = rng.nextDouble() < 0.20;
        return grupoForte ? 35000 + rng.nextDouble() * 25000 : 12000 + rng.nextDouble() * 13000;
    }

    public static double ajustarLanceRacional(double lance) {
        lance = Math.min(lance, ConsorcioConfig.getValorCredito() * 0.50);
        lance = Math.round(lance / 1000.0) * 1000.0;
        return Math.max(10000, lance);
    }
}