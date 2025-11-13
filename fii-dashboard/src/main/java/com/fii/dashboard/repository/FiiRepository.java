package com.fii.dashboard.repository;

import com.fii.dashboard.model.Fii;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FiiRepository extends JpaRepository<Fii, Long> {
    List<Fii> findByPrecoLessThanEqualOrderByDyDesc(double preco);
}

