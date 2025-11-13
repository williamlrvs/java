package com.fii.dashboard.scheduler;

import com.fii.dashboard.service.FiiScraperService;
import com.fii.dashboard.service.FiiService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FiiUpdateScheduler {

    private final FiiScraperService scraperService;

    public FiiUpdateScheduler(FiiScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @Scheduled(fixedRate = 3600000, initialDelay = 5000) // 1h, inicia em 5s
    public void atualizar() {
        scraperService.scrapeAndSaveFiis();
    }
}