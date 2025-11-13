package com.br.code.service;

import com.br.code.config.ConsorcioConfig;
import com.br.code.model.AssemblyData;
import com.br.code.util.FileUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AssemblyService {
    private final List<AssemblyData> allAssembleias;

    public AssemblyService() {
        this.allAssembleias = FileUtils.readAssemblySorteados();
    }

    public List<AssemblyData> getMinhasAssembleias() {
        if (allAssembleias.isEmpty()) {
            System.out.println("ERRO: Arquivo '" + ConsorcioConfig.getAssembleiasFile() + "' não encontrado ou vazio.");
            return new ArrayList<>();
        }

        LocalDate suaEntrada = ConsorcioConfig.getSuaEntrada();
        long antes = allAssembleias.stream().filter(a -> a.date().isBefore(suaEntrada)).count();
        if (antes > 0) {
            System.out.printf("AVISO: %d assembleia(s) antes de %s foram ignoradas.\n", antes, suaEntrada.format(ConsorcioConfig.getDateFormatter()));
        }

        return allAssembleias.stream()
                .filter(a -> !a.date().isBefore(suaEntrada))
                .sorted(Comparator.comparing(AssemblyData::date))
                .collect(Collectors.toList());
    }
}