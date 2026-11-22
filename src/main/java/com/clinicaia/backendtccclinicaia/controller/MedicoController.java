package com.clinicaia.backendtccclinicaia.controller;

import com.clinicaia.backendtccclinicaia.dto.medico.MedicoResponse;
import com.clinicaia.backendtccclinicaia.dto.medico.MedicoUpdateRequest;
import com.clinicaia.backendtccclinicaia.dto.medico.MedicoRequest;
import com.clinicaia.backendtccclinicaia.dto.medico.CadastroMedicoResponse;
import com.clinicaia.backendtccclinicaia.service.MedicoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Expõe consulta da equipe e manutenção do perfil do médico autenticado. */
@RestController
@RequestMapping("/api/medicos")
public class MedicoController {

    private final MedicoService service;

    /**
     * Cria o controlador com o serviço de médicos.
     *
     * @param service regras de acesso e atualização dos profissionais
     */
    public MedicoController(MedicoService service) {
        this.service = service;
    }

    /**
     * Lista os médicos da clínica do médico autenticado.
     *
     * @return médicos disponíveis para cuidado compartilhado
     */
    @GetMapping
    public List<MedicoResponse> listar() {
        return service.listar();
    }

    /**
     * Cadastra o profissional na clínica atual e emite convite para definir senha.
     *
     * @param request dados do novo médico
     * @return HTTP 201 com o médico cadastrado e o estado do convite
     */
    @PostMapping
    public ResponseEntity<CadastroMedicoResponse> criar(@Valid @RequestBody MedicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    /**
     * Busca um médico da mesma clínica.
     *
     * @param id identificador do profissional
     * @return dados públicos do médico
     */
    @GetMapping("/{id}")
    public MedicoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    /**
     * Atualiza o perfil do próprio médico autenticado.
     *
     * @param request dados editáveis
     * @return perfil atualizado do usuário autenticado
     */
    @PutMapping("/me")
    public MedicoResponse atualizarMeuPerfil(@Valid @RequestBody MedicoUpdateRequest request) {
        return service.atualizarMeuPerfil(request);
    }
}
