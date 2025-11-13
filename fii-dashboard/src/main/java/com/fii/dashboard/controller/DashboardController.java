package com.fii.dashboard.controller;

import com.fii.dashboard.repository.FiiRepository;
import com.fii.dashboard.service.FiiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final FiiRepository fiiRepository;
    private final FiiService fiiService;

    public DashboardController(FiiRepository fiiRepository, FiiService fiiService) {
        this.fiiRepository = fiiRepository;
        this.fiiService = fiiService;
    }

    @GetMapping("/")
    public String index(@RequestParam(defaultValue = "1000.0") double maxPreco, Model model) {
        model.addAttribute("fiis", fiiRepository.findAll());
        model.addAttribute("maxPreco", maxPreco);
        model.addAttribute("resumo", fiiService.getResumo(maxPreco)); // AGORA FUNCIONA
        return "index";
    }

    @GetMapping("/atualizar")
    public String atualizar(Model model) {
        // COMENTAR ESSA LINHA
        // fiiService.atualizarDados();

        return "redirect:/";
    }
}