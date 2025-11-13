package com.br.code.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class ConsorcioConfig {
    private static final Properties props = new Properties();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MMM/yy");

    static {
        try (FileInputStream is = new FileInputStream("config.properties")) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar config.properties", e);
        }
    }

    public static int getUserConsortiumNumber() { return Integer.parseInt(props.getProperty("user.consortium.number", "74")); }
    public static LocalDate getSuaEntrada() { return LocalDate.parse(props.getProperty("sua.entrada", "2025-10-28")); }
    public static int getTotalParcelas() { return Integer.parseInt(props.getProperty("total.parcelas", "84")); }
    public static int getParcelasPagas() { return Integer.parseInt(props.getProperty("parcelas.pagas", "2")); }
    public static String getBaseApiUrl() { return props.getProperty("base.api.url"); }
    public static String getCacheFile() { return props.getProperty("cache.file"); }
    public static String getAssembleiasFile() { return props.getProperty("assembleias.file"); }
    public static String getPdfOutput() { return props.getProperty("pdf.output") + String.format("%03d", getUserConsortiumNumber()) + ".pdf"; }
    public static String getChartOutput() { return props.getProperty("chart.output"); }
    public static long getCacheValidityHours() { return Long.parseLong(props.getProperty("cache.validity.hours", "24")); }
    public static int getAtivos() { return Integer.parseInt(props.getProperty("ativos", "252")); }
    public static int getContemplados() { return Integer.parseInt(props.getProperty("contemplados", "12")); }
    public static int getDesistentes() { return Integer.parseInt(props.getProperty("desistentes", "148")); }
    public static YearMonth getProximoMes() { return YearMonth.now().plusMonths(1); }
    public static YearMonth getLimiteProjecao() { return YearMonth.parse(props.getProperty("limite.projecao", "2026-12")); }
    public static double getValorCredito() { return Double.parseDouble(props.getProperty("valor.credito", "160000.00")); }
    public static double getPercentualLanceEmbutido() { return Double.parseDouble(props.getProperty("percentual.lance.embutido", "0.25")); }
    public static double getRecursosVinculados() { return Double.parseDouble(props.getProperty("recursos.vinculados", "397751.88")); }
    public static double getMediaLanceVencedorEstimada() { return Double.parseDouble(props.getProperty("media.lance.vencedor.estimada", "38000.00")); }
    public static double getDesvioPadraoLances() { return Double.parseDouble(props.getProperty("desvio.padrao.lances", "8000.00")); }
    public static double getTaxaDesistenciaMensal() { return Double.parseDouble(props.getProperty("taxa.desistencia.mensal", "0.008")); }

    public static DateTimeFormatter getDateFormatter() { return DATE_FORMATTER; }
    public static DateTimeFormatter getYyyyMmDdFormatter() { return YYYYMMDD_FORMATTER; }
    public static DateTimeFormatter getMesAnoFormatter() { return MES_ANO; }
}