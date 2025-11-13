package com.fii.dashboard.service;

import com.fii.dashboard.model.Fii;
import com.fii.dashboard.repository.FiiRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FiiScraperService {
    private static final Logger log = LoggerFactory.getLogger(FiiScraperService.class);

    private final FiiRepository fiiRepository;

    @Value("${fii.scraper.min-dy:8.0}")
    private double minDy;

    @Value("${fii.scraper.max-pvp:1.2}")
    private double maxPvp;

    @Value("${fii.scraper.min-volume:3000000}")
    private long minVolume;

    public FiiScraperService(FiiRepository fiiRepository) {
        this.fiiRepository = fiiRepository;
    }

    public void scrapeAndSaveFiis() {
        log.info("Iniciando scraping de investidor10.com.br/fiis...");
        List<Fii> fiis = new ArrayList<>();
        int page = 1;
        int totalSaved = 0;
        int recomendados = 0;

        // Descobre número máximo de páginas antes do scraping
        int maxPages = discoverMaxPages();
        log.info("Total de páginas a serem lidas: {}", maxPages);

        try {
            while (page <= maxPages) {
                String url = "https://investidor10.com.br/fiis/?page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();
                Elements rows = doc.select("table tbody tr");
                if (rows.isEmpty()) {
                    log.info("Página {} está vazia. Saindo.", page);
                    break;
                }

                int encontrados = 0;

                for (Element row : rows) {
                    Elements cols = row.select("td");
                    if (cols.isEmpty()) continue; // ignora células vazias/propaganda

                    String raw = cols.get(0).text().trim();

                    Matcher matcher = Pattern.compile("([A-Z]{4}11)").matcher(raw);
                    if (!matcher.find()) continue; // só conta se é FII

                    String ticker = extractTicker(cols.get(0).html());
                    String nome = extractName(cols.get(0).html()); // Ou use extração do nome
                    double dy = parseDoubleOrZero(getInnerValue(cols.get(2).html()).replace("%", "").replace(",", "."));
                    double pvp = parseDoubleOrZero(getInnerValue(cols.get(3).html()));
                    long volume = parseVolume(getInnerValue(cols.get(4).html()));

                    // Se DY lido é mensal:
                    double dyAnual = dy * 12;


                    log.trace("dyAnual: {}", dyAnual);
                    log.trace("dy: {}", dy);
                    log.trace("minDy: {}", minDy);
                    log.trace("pvp: {}", pvp);
                    log.trace("maxPvp: {}", maxPvp);
                    log.trace("volume: {}", volume);
                    log.trace("minVolume: {}", minVolume);

                    boolean recomendado = (dy >= minDy) && (pvp > 0 && pvp <= maxPvp) && (volume >= minVolume);

                    if (recomendado) {
                        recomendados++;
                        fiis.add(new Fii(ticker, nome, 0.0, pvp, dy, volume, recomendado));
                        encontrados++;
                    }
                }

                totalSaved += encontrados;
                log.info("Página {}: +{} FIIs (total: {})", page, encontrados, totalSaved);
                page++;
                Thread.sleep(600);
            }
            fiiRepository.deleteAll();
            fiiRepository.saveAll(fiis);
            fiiRepository.flush();
            log.info("Scraping concluído: {} FIIs salvos ({} recomendados).", totalSaved, recomendados);

        } catch (Exception e) {
            log.error("Erro crítico no scraping", e);
        }
    }

    // Função auxiliar para descobrir o número máximo de páginas
    // Função de utilitário
    private String getInnerValue(String html) {
        // Extrai o texto dentro da div, por exemplo "12,79%" ou "268,63 K"
        return Jsoup.parse(html).text();
    }

    // Regex para extrair ticker
    private String extractTicker(String html) {
        Matcher matcher = Pattern.compile("([A-Z]{4}11)").matcher(html);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    // Regex para extrair nome
    private String extractName(String html) {
        Matcher matcher = Pattern.compile("title=\"([^\"]+)\"").matcher(html);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private int discoverMaxPages() {
        try {
            Document doc = Jsoup.connect("https://investidor10.com.br/fiis")
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            // Seleciona todos os links numerados da lista de paginação
            Elements numberLinks = doc.select("ul.pagination-list li.page-item a.page-link");

            int maxPage = 1;
            for (Element link : numberLinks) {
                String txt = link.text().trim();
                // Só considera texto puramente numérico
                if (txt.matches("^\\d+$")) {
                    int n = Integer.parseInt(txt);
                    if (n > maxPage) maxPage = n;
                }
            }
            log.info("Total de páginas numeradas detectadas: {}", maxPage);
            return maxPage;
        } catch (Exception e) {
            log.warn("Falha ao descobrir paginação, fallback para 6 páginas.", e);
            return 6;
        }
    }


    private String safeText(Element element) {
        return (element != null) ? element.text().trim() : "";
    }

    private double parseDoubleOrZero(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return 0.0;
        try {
            String cleaned = value.replaceAll("[^0-9.,-]", "").replace(",", ".");
            return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private long parseVolume(String value) {
        if (value == null || value.isBlank() || value.equals("-")) return 0L;
        try {
            value = value.trim().toUpperCase().replace("R$", "").replaceAll("\\s", "");
            if (value.endsWith("M")) {
                return (long) (Double.parseDouble(value.replace("M", "").replace(",", ".").trim()) * 1_000_000);
            } else if (value.endsWith("K")) {
                return (long) (Double.parseDouble(value.replace("K", "").replace(",", ".").trim()) * 1_000);
            } else {
                // Assume já em reais, sem milhar (ex: 780 = 780)
                return (long) Double.parseDouble(value.replace(".", "").replace(",", ".").trim());
            }
        } catch (Exception e) {
            return 0L;
        }
    }



    /*
    public static void main(String[] args) {
        new FiiScraperService(null).discoverMaxPages();
    }
     */
}
