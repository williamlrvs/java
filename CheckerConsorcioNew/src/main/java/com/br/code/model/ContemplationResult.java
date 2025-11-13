package com.br.code.model;

import java.time.LocalDate;
import java.util.Objects;

public record ContemplationResult(boolean isDirect, boolean isAdjusted, String type, int hundred, int position) {
    public boolean isContemplated() { return isDirect || isAdjusted; }
}