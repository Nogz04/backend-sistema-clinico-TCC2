package com.clinicaia.backendtccclinicaia.controller;

import com.clinicaia.backendtccclinicaia.dto.consulta.ConsultaResponse;
import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteRequest;
import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteResponse;
import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteUpdateRequest;
import com.clinicaia.backendtccclinicaia.dto.prontuario.ProntuarioResponse;
import com.clinicaia.backendtccclinicaia.service.ConsultaService;
import com.clinicaia.backendtccclinicaia.service.PacienteService;
import com.clinicaia.backendtccclinicaia.service.ProntuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expõe a gestão cadastral e o acesso clínico aos pacientes da clínica do médico.
 */
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;
    private final ProntuarioService prontuarioService;
    private final ConsultaService consultaService;

    /**
     * Cria o controlador com os serviços de paciente, prontuário e consulta.
     *
     * @param pacienteService regras cadastrais e de vínculo
     * @param prontuarioService leitura e consolidação do prontuário único
     * @param consultaService histórico de atendimentos
     */
    public PacienteController(
            PacienteService pacienteService,
            ProntuarioService prontuarioService,
            ConsultaService consultaService
    ) {
        this.pacienteService = pacienteService;
        this.prontuarioService = prontuarioService;
        this.consultaService = consultaService;
    }

    /**
     * Cadastra o paciente na clínica do médico e cria o prontuário único junto com ele.
     *
     * @param request cadastro e prontuário inicial
     * @return HTTP 201 com paciente criado
     */
    @PostMapping
    public ResponseEntity<PacienteResponse> criar(@Valid @RequestBody PacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.criar(request));
    }

    /**
     * Lista os pacientes da clínica do médico autenticado.
     *
     * @return pacientes pertencentes à mesma clínica do médico autenticado
     */
    @GetMapping
    public List<PacienteResponse> listar() {
        return pacienteService.listarMeusPacientes();
    }

    /**
     * Busca um paciente da clínica do médico autenticado.
     *
     * @param id identificador do paciente
     * @return cadastro quando há vínculo de acesso
     */
    @GetMapping("/{id}")
    public PacienteResponse buscar(@PathVariable Long id) {
        return pacienteService.buscar(id);
    }

    /**
     * Atualiza os dados cadastrais do paciente; o CPF não pode ser alterado.
     *
     * @param id identificador do paciente
     * @param request dados alteráveis, sem CPF
     * @return cadastro atualizado
     */
    @PutMapping("/{id}")
    public PacienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody PacienteUpdateRequest request) {
        return pacienteService.atualizar(id, request);
    }

    /**
     * Compartilha o acompanhamento com outro médico já cadastrado.
     *
     * @param pacienteId paciente sob responsabilidade do solicitante
     * @param medicoId novo médico responsável
     * @return paciente com o conjunto atualizado de responsáveis
     */
    @PostMapping("/{pacienteId}/medicos/{medicoId}")
    public PacienteResponse vincularMedico(@PathVariable Long pacienteId, @PathVariable Long medicoId) {
        return pacienteService.vincularMedico(pacienteId, medicoId);
    }

    /**
     * Consulta o prontuário único do paciente.
     *
     * @param id identificador do paciente
     * @return seu prontuário longitudinal único
     */
    @GetMapping("/{id}/prontuario")
    public ProntuarioResponse prontuario(@PathVariable Long id) {
        return prontuarioService.buscarPorPaciente(id);
    }

    /**
     * Lista o histórico de consultas finalizadas do paciente.
     *
     * @param id identificador do paciente
     * @return consultas finalizadas, da mais recente à mais antiga
     */
    @GetMapping("/{id}/consultas")
    public List<ConsultaResponse> historico(@PathVariable Long id) {
        return consultaService.historicoPaciente(id);
    }
}
