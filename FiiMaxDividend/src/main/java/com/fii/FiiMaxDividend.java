package com.fii;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class FiiMaxDividend {
    private static class Fii {
        String ticker;
        String nome;
        double preco;
        double dividendo;
        double dy;

        public Fii(String ticker, String nome, double preco, double dividendo) {
            this.ticker = ticker;
            this.nome = nome;
            this.preco = preco;
            this.dividendo = dividendo;
            this.dy = dividendo / preco;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> tickers = Arrays.asList(
                "GARE11","KNSC11","MXRF11","MCRE11","PVBI11","JSRE11","HSML11",
                "VILG11","RECR11","LVBI11","IRDM11","BTLG11","RBRR11","VISC11",
                "XPLG11","VRTA11","PMLL11","XPML11","TRXF11","HGRU11","TGAR11",
                "MCCI11","KNRI11","HGLG11","RBRY11"
        );
        double investimentoMensal = 500.0;
        List<Fii> fiis = new ArrayList<>();

        // ---- Informe suas cotas atuais ----
        Map<String, Integer> minhasCotas = new HashMap<>();
        minhasCotas.put("MXRF11", 11);
        minhasCotas.put("GARE11", 11);
        // Adicione conforme sua carteira real

        // ---- Coleta dados ----
        for (String ticker : tickers) {
            try {
                String url = "https://investidor10.com.br/fiis/" + ticker.toLowerCase() + "/";
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();

                Element precoEl = doc.selectFirst("span.value");
                double preco = precoEl != null ? parsePreco(precoEl.text()) : 0.0;

                double dividendo = 0.0;
                Elements tables = doc.select("table");
                for (Element table : tables) {
                    for (Element row : table.select("tr")) {
                        Elements cols = row.select("td");
                        if (cols.size() >= 4 && cols.get(0).text().toLowerCase().contains("dividendo")) {
                            dividendo = parsePreco(cols.get(3).text());
                            break;
                        }
                    }
                    if (dividendo > 0) break;
                }

                if (preco > 0 && dividendo > 0) {
                    fiis.add(new Fii(ticker, "", preco, dividendo));
                }
            } catch (Exception e) {
                System.out.println("Erro ao acessar " + ticker + ": " + e.getMessage());
            }
        }

        // ---- FIIs ordenados por DY ----
        fiis.sort((a, b) -> Double.compare(b.dy, a.dy));

        System.out.println("\nFIIs ordenados por DY:");
        System.out.println("Ticker | Preço | Dividendo | DY (%)");
        for (Fii fii : fiis) {
            System.out.printf("%s | Preço: R$%.2f | Dividendo: R$%.2f | DY: %.2f%%\n",
                    fii.ticker, fii.preco, fii.dividendo, fii.dy * 100);
        }

        // ---- Recomendação priorizando FIIs da carteira ----
        double restante = investimentoMensal;
        System.out.println("\nRecomendação ótima (DY alto, priorizando carteira):");
        for (Fii fii : fiis) {
            // Priorize FIIs que já tem, depois os novos (ambos ordenados por DY)
            boolean jaTem = minhasCotas.containsKey(fii.ticker);
            // Se já tem ou se DY está no topo, recomenda
            if (jaTem || fii.dy >= fiis.get(0).dy * 0.95) {
                int qtd = (int) (restante / fii.preco);
                if (qtd > 0) {
                    double investido = qtd * fii.preco;
                    double estimDiv = qtd * fii.dividendo;
                    System.out.printf("%d cotas de %s (R$%.2f cada) = R$%.2f | Dividendos previstos: R$%.2f [%s]\n",
                            qtd, fii.ticker, fii.preco, investido, estimDiv, jaTem ? "CARTEIRA" : "NOVA");
                    restante -= investido;
                }
            }
        }
        System.out.printf("Saldo não utilizado: R$%.2f\n", restante);

        // ---- Estimativa de dividendos do seu portfólio ----
        System.out.println("\nResumo dos dividendos estimados para suas cotas:");
        double totalPortfolioDiv = 0.0;
        for (Fii fii : fiis) {
            if (minhasCotas.containsKey(fii.ticker)) {
                int minhasQtd = minhasCotas.get(fii.ticker);
                double totalDivFii = minhasQtd * fii.dividendo;
                totalPortfolioDiv += totalDivFii;
                System.out.printf("%s: %d cotas x R$%.2f = R$%.2f\n",
                        fii.ticker, minhasQtd, fii.dividendo, totalDivFii);
            }
        }
        System.out.printf("Dividendos totais estimados do portfólio: R$%.2f\n", totalPortfolioDiv);
    }

    private static double parsePreco(String txt) {
        txt = txt.replace("R$", "").replace(".", "").replace(",", ".").replaceAll("[^0-9\\.]", "");
        if (txt.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(txt);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
