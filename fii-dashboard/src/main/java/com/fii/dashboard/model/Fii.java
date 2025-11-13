package com.fii.dashboard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fiis")
public class Fii {

    @Id
    private String ticker;

    private String nome;
    private Double preco;
    private Double pvp;
    private Double dy;
    private Long volume;
    private boolean recomendado;

    // === CONSTRUTOR PADRÃO (OBRIGATÓRIO PRO HIBERNATE) ===
    public Fii() {}

    // === CONSTRUTOR DO SCRAPER ===
    public Fii(String ticker, String nome, double preco, double pvp, double dy, long volume, boolean recomendado) {
        this.ticker = ticker;
        this.nome = nome;
        this.preco = preco;
        this.pvp = pvp;
        this.dy = dy;
        this.volume = volume;
        this.recomendado = recomendado;
    }

    // === GETTERS E SETTERS MANUAIS ===

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Double getPreco() { return preco; }
    public void setPreco(Double preco) { this.preco = preco; }

    public Double getPvp() { return pvp; }
    public void setPvp(Double pvp) { this.pvp = pvp; }

    public Double getDy() { return dy; }
    public void setDy(Double dy) { this.dy = dy; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }

    // MÉTODO QUE VOCÊ QUER: isRecomendado()
    public boolean isRecomendado() {
        return recomendado;
    }

    public void setRecomendado(boolean recomendado) {
        this.recomendado = recomendado;
    }

    @Override
    public String toString() {
        return "Fii{" +
                "ticker='" + ticker + '\'' +
                ", nome='" + nome + '\'' +
                ", preco=" + preco +
                ", pvp=" + pvp +
                ", dy=" + dy +
                ", volume=" + volume +
                ", recomendado=" + recomendado +
                '}';
    }
}