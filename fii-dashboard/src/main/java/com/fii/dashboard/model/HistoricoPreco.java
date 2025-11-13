package com.fii.dashboard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_precos")
public class HistoricoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Funciona!
    private Long id;

    private String ticker;
    private double preco;
    private LocalDateTime data = LocalDateTime.now();

    public HistoricoPreco() {}
    public HistoricoPreco(String ticker, double preco) {
        this.ticker = ticker; this.preco = preco;
    }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }
}