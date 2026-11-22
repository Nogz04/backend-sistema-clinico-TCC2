package com.clinicaia.backendtccclinicaia.controller;

import com.clinicaia.backendtccclinicaia.dto.consulta.AtualizarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.consulta.ConsultaResponse;
import com.clinicaia.backendtccclinicaia.dto.consulta.CriarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.consulta.FinalizarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.ia.AnaliseIaResponse;
import com.clinicaia.backendtccclinicaia.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Expõe o ciclo de vida da consulta, da abertura à confirmação médica. */
@RestController
@RequestMapping("/api/consultas")
public class ConsultaController {

    private final ConsultaService service;

    /**
     * Cria o controlador com o serviço de consultas.
     *
     * @param service regras do atendimento e integração opcional com IA
     */
    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    /**
     * Abre um atendimento em rascunho para um paciente da clínica do médico.
     *
     * @param request paciente e conteúdo inicial
     * @return HTTP 201 com o rascunho criado
     */
    @PostMapping
    public ResponseEntity<ConsultaResponse> criar(@Valid @RequestBody CriarConsultaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    /**
     * Lista os rascunhos do médico autenticado, que só ele enxerga.
     *
     * @return consultas ainda editáveis pertencentes ao médico autenticado
     */
    @GetMapping("/rascunhos")
    public List<ConsultaResponse> listarMeusRascunhos() {
        return service.listarMeusRascunhos();
    }

    /**
     * Busca uma consulta; rascunhos só são visíveis ao médico autor.
     *
     * @param id identificador da consulta
     * @return consulta respeitando vínculo e privacidade do rascunho
     */
    @GetMapping("/{id}")
    public ConsultaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    /**
     * Substitui o conteúdo de um rascunho; consultas finalizadas são imutáveis.
     *
     * @param id identificador do rascunho
     * @param request conteúdo revisado
     * @return rascunho atualizado
     */
    @PutMapping("/{id}")
    public ConsultaResponse atualizar(@PathVariable Long id, @Valid @RequestBody AtualizarConsultaRequest request) {
        return service.atualizar(id, request);
    }

    /**
     * Descarta definitivamente um rascunho que não deve entrar no histórico.
     *
     * @param id identificador do rascunho pertencente ao médico autenticado
     * @return HTTP 204 quando o descarte é concluído
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarRascunho(@PathVariable Long id) {
        service.cancelarRascunho(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Solicita análise opcional da transcrição pela Gemini.
     *
     * @param id identificador do rascunho do médico atual
     * @return SOAP e sugestões consultivas estruturadas
     */
    @PostMapping("/{id}/analise-ia")
    public AnaliseIaResponse analisar(@PathVariable Long id) {
        return service.analisarComIa(id);
    }

    /**
     * Confirma a consulta e consolida dados longitudinais revisados.
     *
     * @param id identificador do rascunho
     * @param request SOAP completo e dados confirmados
     * @return consulta imutável em estado FINALIZADA
     */
    @PostMapping("/{id}/finalizar")
    public ConsultaResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarConsultaRequest request) {
        return service.finalizar(id, request);
    }
}
