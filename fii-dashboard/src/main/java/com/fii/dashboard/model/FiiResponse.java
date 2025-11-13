package com.fii.dashboard.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class FiiResponse {

    private List<FiiData> data;

    public List<FiiData> getData() {
        return data;
    }

    public void setData(List<FiiData> data) {
        this.data = data;
    }

    public static class FiiData {
        @JsonProperty("ticker")
        private String ticker;

        @JsonProperty("setor")
        private String setor;

        @JsonProperty("preco")
        private double preco;

        @JsonProperty("p_vp")
        private double pvp;

        @JsonProperty("dy")
        private double dy;

        @JsonProperty("volume_medio")
        private long volume;

        // GETTERS
        public String getTicker() {
            return ticker;
        }

        public String getSetor() {
            return setor;
        }

        public double getPreco() {
            return preco;
        }

        public double getPvp() {
            return pvp;
        }

        public double getDy() {
            return dy;
        }

        public long getVolume() {
            return volume;
        }

        // SETTERS
        public void setTicker(String ticker) {
            this.ticker = ticker;
        }

        public void setSetor(String setor) {
            this.setor = setor;
        }

        public void setPreco(double preco) {
            this.preco = preco;
        }

        public void setPvp(double pvp) {
            this.pvp = pvp;
        }

        public void setDy(double dy) {
            this.dy = dy;
        }

        public void setVolume(long volume) {
            this.volume = volume;
        }
    }
}
