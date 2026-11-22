package com.clinicaia.backendtccclinicaia.controller;

import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse;
import com.clinicaia.backendtccclinicaia.service.UsoIaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expõe o consumo de tokens da IA do médico autenticado e da sua clínica. */
@RestController
@RequestMapping("/api/ia")
public class UsoIaController {

    private final UsoIaService service;

    /**
     * Cria o controlador com o serviço de consumo de IA.
     *
     * @param service registro e consolidação do consumo
     */
    public UsoIaController(UsoIaService service) {
        this.service = service;
    }

    /**
     * Monta o painel de consumo de tokens.
     *
     * @return painel de uso: totais pessoais, totais da clínica, ranking por médico e série diária
     */
    @GetMapping("/uso")
    public UsoIaPainelResponse uso() {
        return service.painel();
    }
}
