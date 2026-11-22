package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteRequest;
import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteResponse;
import com.clinicaia.backendtccclinicaia.dto.paciente.PacienteUpdateRequest;
import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.Paciente;
import com.clinicaia.backendtccclinicaia.model.Prontuario;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.repository.PacienteRepository;
import com.clinicaia.backendtccclinicaia.repository.ProntuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Cadastro e consulta de pacientes, com o isolamento por clínica.
 *
 * <p>O paciente pertence à clínica do médico que o cadastra e é acessível a todos os médicos dela. O prontuário
 * único é criado junto com o paciente. CPF, telefone e CEP são guardados somente com dígitos, e o e-mail em minúsculas.</p>
 */
@Service
public class PacienteService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final PacienteRepository pacienteRepository;
    private final ProntuarioRepository prontuarioRepository;
    private final MedicoRepository medicoRepository;
    private final MedicoAtualService medicoAtualService;
    private final MedicoService medicoService;

    /**
     * Cria o serviço com suas dependências.
     *
     * @param pacienteRepository persistência dos pacientes
     * @param prontuarioRepository persistência do prontuário único
     * @param medicoRepository persistência do vínculo médico-paciente
     * @param medicoAtualService identidade do médico autenticado
     * @param medicoService busca de médicos restrita à clínica
     */
    public PacienteService(
            PacienteRepository pacienteRepository,
            ProntuarioRepository prontuarioRepository,
            MedicoRepository medicoRepository,
            MedicoAtualService medicoAtualService,
            MedicoService medicoService
    ) {
        this.pacienteRepository = pacienteRepository;
        this.prontuarioRepository = prontuarioRepository;
        this.medicoRepository = medicoRepository;
        this.medicoAtualService = medicoAtualService;
        this.medicoService = medicoService;
    }

    /**
     * Cadastra o paciente, vincula o médico autenticado a ele e cria o prontuário único.
     *
     * @param request dados do paciente e, opcionalmente, o conteúdo inicial do prontuário
     * @return paciente cadastrado
     * @throws ApiException HTTP 409 se já houver paciente com o mesmo CPF
     */
    @Transactional
    public PacienteResponse criar(PacienteRequest request) {
        Medico medico = medicoAtualService.obter();
        String cpf = digitos(request.cpf());
        if (pacienteRepository.existsByCpf(cpf)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Já existe paciente cadastrado com este CPF; vincule o médico ao cadastro existente"
            );
        }
        Paciente paciente = new Paciente();
        paciente.setCpf(cpf);
        paciente.setDataCriacao(LocalDateTime.now(ZONE_ID));
        paciente.setClinica(medico.getClinica());
        aplicarDados(paciente, request);
        paciente = pacienteRepository.save(paciente);

        medico.vincularPaciente(paciente);
        medicoRepository.save(medico);

        Prontuario prontuario = novoProntuario(paciente, request.prontuarioInicial());
        prontuario = prontuarioRepository.save(prontuario);
        paciente.setProntuario(prontuario);
        return toResponse(paciente);
    }

    /**
     * Lista os pacientes da clínica do médico autenticado.
     *
     * @return pacientes da clínica do médico autenticado, em ordem alfabética
     */
    @Transactional
    public List<PacienteResponse> listarMeusPacientes() {
        Medico medico = medicoAtualService.obter();
        return pacienteRepository.findTodosDaClinica(medico.getClinica().getId()).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Busca um paciente restrito à clínica do médico autenticado.
     *
     * @param id identificador do paciente
     * @return paciente da clínica do solicitante
     * @throws ApiException HTTP 404 se não existir nesta clínica
     */
    @Transactional
    public PacienteResponse buscar(Long id) {
        return toResponse(obterComAcesso(id, medicoAtualService.obter()));
    }

    /**
     * Atualiza os dados cadastrais do paciente. O CPF não pode ser alterado.
     *
     * @param id identificador do paciente
     * @param request novos dados cadastrais
     * @return paciente atualizado
     * @throws ApiException HTTP 404 se não existir nesta clínica
     */
    @Transactional
    public PacienteResponse atualizar(Long id, PacienteUpdateRequest request) {
        Paciente paciente = obterComAcesso(id, medicoAtualService.obter());
        paciente.setNomeCompleto(request.nomeCompleto().trim());
        paciente.setDataNascimento(request.dataNascimento());
        paciente.setPlanoSaude(limpar(request.planoSaude()));
        paciente.setTelefone(digitosOuNulo(request.telefone()));
        paciente.setEmail(emailOuNulo(request.email()));
        paciente.setSexo(limpar(request.sexo()));
        paciente.setNomeMae(limpar(request.nomeMae()));
        paciente.setNaturalidadeMunicipio(limpar(request.naturalidadeMunicipio()));
        paciente.setNaturalidadeEstado(uf(request.naturalidadeEstado()));
        paciente.setLogradouro(limpar(request.logradouro()));
        paciente.setNumero(limpar(request.numero()));
        paciente.setComplemento(limpar(request.complemento()));
        paciente.setBairro(limpar(request.bairro()));
        paciente.setMunicipio(limpar(request.municipio()));
        paciente.setEstado(uf(request.estado()));
        paciente.setCep(digitosOuNulo(request.cep()));
        return toResponse(pacienteRepository.save(paciente));
    }

    /**
     * Acrescenta um médico da mesma clínica entre os responsáveis pelo paciente. A operação é idempotente.
     *
     * @param pacienteId identificador do paciente
     * @param medicoId identificador do médico a vincular
     * @return paciente com a equipe atualizada
     * @throws ApiException HTTP 404 se o paciente ou o médico não pertencerem à clínica do solicitante
     */
    @Transactional
    public PacienteResponse vincularMedico(Long pacienteId, Long medicoId) {
        Medico solicitante = medicoAtualService.obter();
        Paciente paciente = obterComAcesso(pacienteId, solicitante);
        Medico novoResponsavel = medicoService.obter(medicoId);
        if (paciente.getMedicosResponsaveis().stream().anyMatch(medico -> medico.getId().equals(medicoId))) {
            return toResponse(paciente);
        }
        novoResponsavel.vincularPaciente(paciente);
        medicoRepository.save(novoResponsavel);
        return toResponse(paciente);
    }

    /**
     * Ponto único de autorização de acesso a um paciente: só o encontra se pertencer à clínica do médico.
     *
     * @param pacienteId identificador do paciente
     * @param medico médico que solicita o acesso
     * @return entidade do paciente
     * @throws ApiException HTTP 404 se não existir nesta clínica
     */
    @Transactional
    public Paciente obterComAcesso(Long pacienteId, Medico medico) {
        return pacienteRepository.findDaClinica(pacienteId, medico.getClinica().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Paciente não encontrado nesta clínica"));
    }

    /**
     * Monta o prontuário único do paciente, com o conteúdo inicial opcional.
     *
     * @param paciente proprietário do prontuário
     * @param initial alergias, medicamentos de uso contínuo e histórico familiar iniciais; pode ser nulo
     * @return prontuário ainda não persistido
     */
    private Prontuario novoProntuario(Paciente paciente, ConsolidacaoProntuarioRequest initial) {
        LocalDateTime now = LocalDateTime.now(ZONE_ID);
        Prontuario prontuario = new Prontuario();
        prontuario.setPaciente(paciente);
        prontuario.setDataCriacao(now);
        prontuario.setDataUltimaAtualizacao(now);
        if (initial != null) {
            prontuario.setAlergiasConhecidas(limpar(initial.alergiasConhecidas()));
            prontuario.setMedicamentosUsoContinuo(limpar(initial.medicamentosUsoContinuo()));
            prontuario.setHistoricoFamiliar(limpar(initial.historicoFamiliar()));
        }
        return prontuario;
    }

    /**
     * Copia os dados cadastrais da requisição, normalizando telefone, CEP, e-mail e UF.
     *
     * @param paciente entidade de destino
     * @param request dados informados no cadastro
     */
    private void aplicarDados(Paciente paciente, PacienteRequest request) {
        paciente.setNomeCompleto(request.nomeCompleto().trim());
        paciente.setDataNascimento(request.dataNascimento());
        paciente.setPlanoSaude(limpar(request.planoSaude()));
        paciente.setTelefone(digitosOuNulo(request.telefone()));
        paciente.setEmail(emailOuNulo(request.email()));
        paciente.setSexo(limpar(request.sexo()));
        paciente.setNomeMae(limpar(request.nomeMae()));
        paciente.setNaturalidadeMunicipio(limpar(request.naturalidadeMunicipio()));
        paciente.setNaturalidadeEstado(uf(request.naturalidadeEstado()));
        paciente.setLogradouro(limpar(request.logradouro()));
        paciente.setNumero(limpar(request.numero()));
        paciente.setComplemento(limpar(request.complemento()));
        paciente.setBairro(limpar(request.bairro()));
        paciente.setMunicipio(limpar(request.municipio()));
        paciente.setEstado(uf(request.estado()));
        paciente.setCep(digitosOuNulo(request.cep()));
    }

    /**
     * Converte o paciente na resposta enviada ao cliente.
     *
     * @param paciente entidade carregada
     * @return representação enviada ao cliente, com a equipe de médicos em ordem alfabética
     */
    public PacienteResponse toResponse(Paciente paciente) {
        LinkedHashSet<PacienteResponse.MedicoResumo> medicos = paciente.getMedicosResponsaveis().stream()
                .sorted(java.util.Comparator.comparing(Medico::getNomeCompleto))
                .map(medico -> new PacienteResponse.MedicoResumo(medico.getId(), medico.getNomeCompleto(), medico.getCrm()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new PacienteResponse(
                paciente.getId(), paciente.getNomeCompleto(), paciente.getCpf(), paciente.getDataNascimento(),
                paciente.getPlanoSaude(), paciente.getTelefone(), paciente.getEmail(), paciente.getSexo(), paciente.getNomeMae(),
                paciente.getNaturalidadeMunicipio(), paciente.getNaturalidadeEstado(), paciente.getLogradouro(), paciente.getNumero(),
                paciente.getComplemento(), paciente.getBairro(), paciente.getMunicipio(), paciente.getEstado(), paciente.getCep(),
                paciente.getDataCriacao(), paciente.getProntuario() == null ? null : paciente.getProntuario().getId(), medicos
        );
    }

    /**
     * Remove espaços das pontas; texto vazio vira {@code null}.
     *
     * @param value texto opcional
     * @return texto sem espaços nas pontas, ou {@code null} se estiver vazio
     */
    private String limpar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Remove a máscara de documentos como CPF.
     *
     * @param value valor com ou sem pontuação
     * @return o valor sem qualquer caractere que não seja dígito
     */
    private String digitos(String value) {
        return value.replaceAll("\\D", "");
    }

    /**
     * Normaliza telefone e CEP, que são guardados somente com dígitos.
     *
     * @param value valor opcional, com ou sem máscara
     * @return somente os dígitos do valor, ou {@code null} se estiver vazio
     */
    private String digitosOuNulo(String value) {
        String clean = limpar(value);
        return clean == null ? null : digitos(clean);
    }

    /**
     * Normaliza o e-mail para minúsculas.
     *
     * @param value e-mail opcional
     * @return e-mail em minúsculas, ou {@code null} se estiver vazio
     */
    private String emailOuNulo(String value) {
        String clean = limpar(value);
        return clean == null ? null : clean.toLowerCase(Locale.ROOT);
    }

    /**
     * Normaliza a sigla do estado para maiúsculas.
     *
     * @param value sigla opcional
     * @return sigla da UF em maiúsculas, ou {@code null} se estiver vazia
     */
    private String uf(String value) {
        String clean = limpar(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }
}
