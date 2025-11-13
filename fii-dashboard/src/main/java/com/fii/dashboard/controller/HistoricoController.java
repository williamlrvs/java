package com.fii.dashboard.controller;

import com.fii.dashboard.model.HistoricoPreco;
import com.fii.dashboard.repository.HistoricoPrecoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historico")
public class HistoricoController {
    private final HistoricoPrecoRepository repo;

    public HistoricoController(HistoricoPrecoRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{ticker}")
    public List<HistoricoPreco> get(@PathVariable String ticker) {
        return repo.findByTickerOrderByDataDesc(ticker);
    }
}