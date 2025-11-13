package com.br.code.main;

import com.br.code.orchestrator.ConsorcioOrchestrator;

public class CheckerConsorcioMain {
    public static void main(String[] args) {
        try {
            new ConsorcioOrchestrator().executar();
        } catch (Exception e) {
            System.err.println("Falha crítica: " + e.getMessage());
            e.printStackTrace();
        }
    }
}