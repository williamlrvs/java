package com.fii.dashboard.repository;

import com.fii.dashboard.model.HistoricoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {
    List<HistoricoPreco> findByTickerOrderByDataDesc(String ticker);
}