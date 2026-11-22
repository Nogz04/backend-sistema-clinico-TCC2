package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.medico.MedicoRequest;
import com.clinicaia.backendtccclinicaia.dto.medico.MedicoResponse;
import com.clinicaia.backendtccclinicaia.dto.medico.MedicoUpdateRequest;
import com.clinicaia.backendtccclinicaia.dto.medico.CadastroMedicoResponse;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.Clinica;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cadastro e consulta de médicos da clínica.
 *
 * <p>Todo médico pertence a exatamente uma clínica: a do médico que o cadastra. E-mail, CPF e CRM são únicos
 * no sistema. O cadastro cria a conta sem senha utilizável, e o acesso é ativado por um convite de uso único.</p>
 */
@Service
public class MedicoService {

    private final MedicoRepository medicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MedicoAtualService medicoAtualService;
    private final ConviteMedicoService conviteMedicoService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Cria o serviço com suas dependências.
     *
     * @param medicoRepository persistência dos médicos
     * @param usuarioRepository verificação de e-mail único entre todos os usuários
     * @param passwordEncoder codifica a senha provisória
     * @param medicoAtualService identidade do médico autenticado
     * @param conviteMedicoService emissão do convite de ativação
     */
    public MedicoService(
            MedicoRepository medicoRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            MedicoAtualService medicoAtualService,
            ConviteMedicoService conviteMedicoService
    ) {
        this.medicoRepository = medicoRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.medicoAtualService = medicoAtualService;
        this.conviteMedicoService = conviteMedicoService;
    }

    /**
     * Cadastra um médico na clínica do médico autenticado e emite o convite de ativação.
     *
     * @param request dados do novo médico
     * @return médico cadastrado e situação do convite
     * @throws ApiException HTTP 409 se o e-mail, o CPF ou o CRM já estiverem cadastrados
     */
    @Transactional
    public CadastroMedicoResponse criar(MedicoRequest request) {
        Medico medicoLogado = medicoAtualService.obter();
        Medico medico = cadastrar(request, medicoLogado.getClinica());
        ConviteMedicoService.ConviteEmitido convite = conviteMedicoService.emitir(medico, medicoLogado);
        return new CadastroMedicoResponse(
                toResponse(medico), convite.enviadoPorEmail(), convite.expiraEm(), convite.urlDesenvolvimento()
        );
    }

    /**
     * Lista os médicos da clínica do médico autenticado.
     *
     * @return médicos da clínica do médico autenticado, em ordem alfabética
     */
    @Transactional(readOnly = true)
    public List<MedicoResponse> listar() {
        Medico atual = medicoAtualService.obter();
        return medicoRepository.findByClinicaIdOrderByNomeCompletoAsc(atual.getClinica().getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Busca um médico restrito à clínica do solicitante.
     *
     * @param id identificador do médico
     * @return médico da mesma clínica do solicitante
     * @throws ApiException HTTP 404 se não existir na clínica
     */
    @Transactional(readOnly = true)
    public MedicoResponse buscar(Long id) {
        Medico atual = medicoAtualService.obter();
        return toResponse(medicoRepository.findByIdAndClinicaId(id, atual.getClinica().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Médico não encontrado na clínica")));
    }

    /**
     * Atualiza os dados editáveis do próprio perfil. CRM e CPF não podem ser alterados.
     *
     * @param request nome, e-mail, data de nascimento e especialidades
     * @return perfil atualizado
     * @throws ApiException HTTP 409 se o novo e-mail pertencer a outro usuário
     */
    @Transactional
    public MedicoResponse atualizarMeuPerfil(MedicoUpdateRequest request) {
        Medico medico = medicoAtualService.obter();
        String email = normalizarEmail(request.email());
        if (!medico.getEmail().equalsIgnoreCase(email) && usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        medico.setNomeCompleto(request.nomeCompleto().trim());
        medico.setEmail(email);
        medico.setDataNascimento(request.dataNascimento());
        medico.setEspecialidades(normalizarEspecialidades(request.especialidades()));
        return toResponse(medicoRepository.save(medico));
    }

    /**
     * Localiza um médico restrito à clínica do solicitante, para uso por outros serviços.
     *
     * @param id identificador do médico
     * @return entidade do médico
     * @throws ApiException HTTP 404 se não existir na clínica
     */
    public Medico obter(Long id) {
        Medico atual = medicoAtualService.obter();
        return medicoRepository.findByIdAndClinicaId(id, atual.getClinica().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Médico não encontrado na clínica"));
    }

    /**
     * Cria o médico com senha aleatória inutilizável e {@code senhaDefinida = false}, de modo que a conta só
     * possa ser acessada depois do convite.
     *
     * @param request dados do novo médico
     * @param clinica clínica de destino
     * @return médico persistido
     */
    private Medico cadastrar(MedicoRequest request, Clinica clinica) {
        String email = normalizarEmail(request.email());
        String cpf = somenteDigitos(request.cpf());
        String crm = request.crm().trim().toUpperCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (medicoRepository.existsByCpf(cpf)) {
            throw new ApiException(HttpStatus.CONFLICT, "CPF já cadastrado para outro médico");
        }
        if (medicoRepository.existsByCrmIgnoreCase(crm)) {
            throw new ApiException(HttpStatus.CONFLICT, "CRM já cadastrado");
        }
        Medico medico = new Medico();
        medico.setNomeCompleto(request.nomeCompleto().trim());
        medico.setEmail(email);
        byte[] placeholder = new byte[32];
        secureRandom.nextBytes(placeholder);
        medico.setSenha(passwordEncoder.encode(Base64.getEncoder().encodeToString(placeholder)));
        medico.setSenhaDefinida(false);
        medico.setCrm(crm);
        medico.setCpf(cpf);
        medico.setDataNascimento(request.dataNascimento());
        medico.setClinica(clinica);
        medico.setEspecialidades(normalizarEspecialidades(request.especialidades()));
        return medicoRepository.save(medico);
    }

    /**
     * Remove espaços e itens em branco das especialidades.
     *
     * @param values especialidades informadas, possivelmente nulas
     * @return conjunto sem espaços nas pontas, sem itens em branco e com a ordem original
     */
    private LinkedHashSet<String> normalizarEspecialidades(java.util.Set<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        return values.stream().map(String::trim).filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Converte o médico na resposta enviada ao cliente.
     *
     * @param medico entidade carregada
     * @return representação enviada ao cliente
     */
    public MedicoResponse toResponse(Medico medico) {
        return new MedicoResponse(
                medico.getId(), medico.getNomeCompleto(), medico.getEmail(), medico.getCrm(), medico.getCpf(),
                medico.getDataNascimento(), medico.getClinicaAtuacao(), new LinkedHashSet<>(medico.getEspecialidades())
        );
    }

    /**
     * Normaliza o e-mail, que é o login e precisa ser comparável.
     *
     * @param email e-mail informado
     * @return e-mail sem espaços nas pontas e em minúsculas
     */
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Remove a máscara do CPF.
     *
     * @param value valor com ou sem pontuação
     * @return o valor sem qualquer caractere que não seja dígito
     */
    private String somenteDigitos(String value) {
        return value.replaceAll("\\D", "");
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
}
