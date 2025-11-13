// src/main/java/com/fii/dashboard/service/FiiService.java
package com.fii.dashboard.service;

import com.fii.dashboard.model.Fii;
import com.fii.dashboard.model.FiiResponse;
import com.fii.dashboard.model.HistoricoPreco;
import com.fii.dashboard.repository.FiiRepository;
import com.fii.dashboard.repository.HistoricoPrecoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class FiiService {

    private static final Logger log = LoggerFactory.getLogger(FiiService.class);
    private static final String API_URL = "https://www.fundsexplorer.com.br/wp-json/funds-explorer/v1/funds";

    private final FiiRepository fiiRepo;
    private final HistoricoPrecoRepository histRepo;
    private final ConfigService config;

    private final RestTemplate restTemplate = new RestTemplate();

    public FiiService(FiiRepository fiiRepo, HistoricoPrecoRepository histRepo, ConfigService config) {
        this.fiiRepo = fiiRepo;
        this.histRepo = histRepo;
        this.config = config;
    }

    public void atualizarDados() {
        log.info("Iniciando atualização via API JSON do Funds Explorer...");

        try {
            FiiResponse response = baixarDadosJson();
            List<Fii> novosFiis = converterParaFii(response);

            if (novosFiis.isEmpty()) {
                log.warn("Nenhum FII carregado. Verifique os dados.");
                return;
            }

            log.info("Limpando {} FIIs antigos...", fiiRepo.count());
            fiiRepo.deleteAllInBatch();

            log.info("Salvando {} novos FIIs...", novosFiis.size());
            fiiRepo.saveAll(novosFiis);

            log.info("FIIs atualizados com sucesso: {} registros.", novosFiis.size());

        } catch (Exception e) {
            log.error("Falha ao atualizar FIIs", e);
        }
    }

    private FiiResponse baixarDadosJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        headers.set("Accept", "application/json");
        headers.set("Referer", "https://www.fundsexplorer.com.br/ranking");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<FiiResponse> response = restTemplate.exchange(
                API_URL, HttpMethod.GET, entity, FiiResponse.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("Falha na API: " + response.getStatusCode());
        }

        log.info("JSON baixado com sucesso! {} FIIs recebidos.", response.getBody().getData().size());
        return response.getBody();
    }

    private List<Fii> converterParaFii(FiiResponse response) {
        List<Fii> fiis = new ArrayList<>();
        List<HistoricoPreco> historicos = new ArrayList<>();

        double minDy = config.getDouble("min_dy", 0.06);
        double maxPvp = config.getDouble("max_pvp", 1.1);
        long minVol = (long) config.getDouble("min_volume", 50000);

        log.info("Filtros: DY > {:.2f}%, P/VP < {}, Volume > {}", minDy * 100, maxPvp, minVol);

        for (FiiResponse.FiiData data : response.getData()) {
            String ticker = data.getTicker();
            if (ticker == null || ticker.isBlank()) continue;

            double preco = data.getPreco();
            if (preco <= 0) continue;

            String nome = data.getSetor() != null ? data.getSetor() : "Desconhecido";
            double pvp = data.getPvp();
            double dy = data.getDy() / 100.0; // vem em %
            long volume = data.getVolume();

            boolean recomendado = pvp > 0 && pvp < maxPvp && dy > minDy && volume > minVol;

            fiis.add(new Fii(ticker, nome, preco, pvp, dy, volume, recomendado));
            historicos.add(new HistoricoPreco(ticker, preco));
        }

        if (!historicos.isEmpty()) {
            histRepo.saveAll(historicos);
            log.info("Salvos {} históricos de preço.", historicos.size());
        }

        return fiis;
    }

    public static class ResumoFii {
        public final int total;
        public final int recomendados;
        public final double dyMedio;

        public ResumoFii(int total, int recomendados, double dyMedio) {
            this.total = total;
            this.recomendados = recomendados;
            this.dyMedio = dyMedio;
        }
    }

    public ResumoFii getResumo(double maxPreco) {
        List<Fii> fiis = fiiRepo.findAll();

        long total = fiis.size();
        long recomendados = fiis.stream()
                .filter(f -> f.getPreco() <= maxPreco && f.isRecomendado())
                .count();

        double dyMedio = fiis.stream()
                .filter(f -> f.getPreco() <= maxPreco && f.isRecomendado())
                .mapToDouble(Fii::getDy)
                .average()
                .orElse(0.0) * 100; // em %

        return new ResumoFii((int) total, (int) recomendados, dyMedio);
    }
}