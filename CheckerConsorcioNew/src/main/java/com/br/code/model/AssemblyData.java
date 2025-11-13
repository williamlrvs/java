package com.br.code.model;

import java.time.LocalDate;
import java.util.Objects;

public record AssemblyData(LocalDate date, Integer sorteada, Integer vencedora) {
    public AssemblyData { Objects.requireNonNull(date); }
}