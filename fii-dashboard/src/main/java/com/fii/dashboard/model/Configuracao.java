package com.fii.dashboard.model;

import jakarta.persistence.*;

@Entity
@Table(name = "configuracoes")
public class Configuracao {

    @Id
    private String chave;
    private String valor;

    public Configuracao() {}
    public Configuracao(String chave, String valor) {
        this.chave = chave; this.valor = valor;
    }

    public String getChave() { return chave; }
    public void setChave(String chave) { this.chave = chave; }
    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }
}