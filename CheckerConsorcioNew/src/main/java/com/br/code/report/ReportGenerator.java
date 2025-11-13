package com.br.code.report;

import com.br.code.config.ConsorcioConfig;
import com.br.code.model.AssemblyData;
import com.br.code.model.ContemplationResult;
import com.br.code.service.CalculationService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.JSONObject;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.List;

public class ReportGenerator {
    private final CalculationService calcService;

    public ReportGenerator(CalculationService calcService) {
        this.calcService = calcService;
    }

    public void gerarGraficoEvolucao(DefaultCategoryDataset dataset) {
        // ... (mesmo código de antes)
    }

    public void gerarPDF(List<AssemblyData> minhas, List<ContemplationResult> resultados, List<JSONObject> sorteios, double taxaGrupo, List<YearMonth> mesesProjetados) {
        // ... (mesmo código de antes)
    }

    private String gerarBarra(double chance, int tamanho) {
        int preenchido = (int) (chance * tamanho);
        StringBuilder b = new StringBuilder();
        b.append("█".repeat(Math.min(tamanho, preenchido)));
        if (preenchido < tamanho) b.append("░");
        return String.format("%-" + (tamanho + 1) + "s", b);
    }
}