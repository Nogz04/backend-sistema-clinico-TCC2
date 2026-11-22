package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.consulta.AtualizarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.consulta.ConsultaResponse;
import com.clinicaia.backendtccclinicaia.dto.consulta.CriarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.consulta.FinalizarConsultaRequest;
import com.clinicaia.backendtccclinicaia.dto.consulta.SoapDto;
import com.clinicaia.backendtccclinicaia.dto.ia.AnaliseIaResponse;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoAnaliseDto;
import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Consulta;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.Paciente;
import com.clinicaia.backendtccclinicaia.model.Prontuario;
import com.clinicaia.backendtccclinicaia.model.StatusConsulta;
import com.clinicaia.backendtccclinicaia.repository.ConsultaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestra o atendimento clínico, da abertura do rascunho à consolidação no prontuário.
 *
 * <p>A consulta percorre etapas explícitas: criação como {@code RASCUNHO}, análise opcional pela IA e
 * finalização. Enquanto é rascunho, só o médico autor a vê e a altera. A finalização exige os quatro eixos
 * do SOAP revisados, torna a consulta imutável e atualiza o prontuário apenas com os dados confirmados.</p>
 *
 * <p>As sugestões da IA são gravadas separadamente do conteúdo confirmado e nunca alteram o prontuário
 * por conta própria, o que preserva a decisão do médico e permite continuar sem a IA quando ela estiver
 * indisponível.</p>
 */
@Service
public class ConsultaService {

    /** Fuso das datas clínicas, fixo para não depender da configuração do servidor. */
    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final ConsultaRepository consultaRepository;
    private final MedicoAtualService medicoAtualService;
    private final PacienteService pacienteService;
    private final ProntuarioService prontuarioService;
    private final GeminiService geminiService;
    private final UsoIaService usoIaService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transacao;

    /** Teto de caracteres da transcrição enviada à IA, para limitar o gasto de tokens. */
    private final int maxCaracteresEntrada;

    /** Consultas com análise em andamento nesta instância; evita chamadas duplicadas. */
    private final Set<Long> emAnalise = ConcurrentHashMap.newKeySet();

    /**
     * Cria o serviço com suas dependências.
     *
     * @param consultaRepository persistência e histórico
     * @param medicoAtualService identidade do autor
     * @param pacienteService autorização de acesso
     * @param prontuarioService acesso e consolidação longitudinal
     * @param geminiService estruturação consultiva opcional
     * @param usoIaService registro e limite de consumo de tokens
     * @param objectMapper guarda e relê a última análise para reaproveitá-la
     * @param transactionManager permite transações curtas ao redor da chamada externa
     * @param maxCaracteresEntrada teto da transcrição enviada à IA, para limitar o gasto de tokens
     */
    public ConsultaService(
            ConsultaRepository consultaRepository,
            MedicoAtualService medicoAtualService,
            PacienteService pacienteService,
            ProntuarioService prontuarioService,
            GeminiService geminiService,
            UsoIaService usoIaService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${gemini.max-input-chars:20000}") int maxCaracteresEntrada
    ) {
        this.consultaRepository = consultaRepository;
        this.medicoAtualService = medicoAtualService;
        this.pacienteService = pacienteService;
        this.prontuarioService = prontuarioService;
        this.geminiService = geminiService;
        this.usoIaService = usoIaService;
        this.objectMapper = objectMapper;
        this.transacao = new TransactionTemplate(transactionManager);
        this.maxCaracteresEntrada = maxCaracteresEntrada;
    }

    /**
     * Abre um atendimento editável para paciente da mesma clínica.
     *
     * @param request paciente e conteúdo inicial
     * @return consulta em estado RASCUNHO
     */
    @Transactional
    public ConsultaResponse criar(CriarConsultaRequest request) {
        Medico medico = medicoAtualService.obter();
        Paciente paciente = pacienteService.obterComAcesso(request.pacienteId(), medico);
        Prontuario prontuario = prontuarioService.obterPorPaciente(paciente.getId());

        Consulta consulta = new Consulta();
        consulta.setMedico(medico);
        consulta.setProntuario(prontuario);
        consulta.setDataHoraConsulta(LocalDateTime.now(ZONE_ID));
        consulta.setStatus(StatusConsulta.RASCUNHO);
        consulta.setTranscricaoOriginal(limpar(request.transcricaoOriginal()));
        consulta.setDadosGeraisConsulta(limpar(request.dadosGeraisConsulta()));
        aplicarSoap(consulta, request.soap());
        return toResponse(consultaRepository.save(consulta));
    }

    /**
     * Substitui o conteúdo editável de um rascunho do próprio autor.
     *
     * @param id identificador da consulta
     * @param request transcrição, anotações e SOAP atuais
     * @return rascunho atualizado
     */
    @Transactional
    public ConsultaResponse atualizar(Long id, AtualizarConsultaRequest request) {
        Consulta consulta = obterRascunhoDoMedico(id);
        consulta.setTranscricaoOriginal(limpar(request.transcricaoOriginal()));
        consulta.setDadosGeraisConsulta(limpar(request.dadosGeraisConsulta()));
        aplicarSoap(consulta, request.soap());
        return toResponse(consultaRepository.save(consulta));
    }

    /**
     * Exclui um rascunho do próprio autor.
     *
     * <p>Rascunhos não integram o prontuário nem o histórico clínico, por isso podem ser removidos para
     * que atendimentos abandonados não se acumulem. Consultas finalizadas nunca são excluídas.</p>
     *
     * @param id identificador do rascunho
     * @throws ApiException com HTTP 403 para outro autor ou HTTP 409 para consulta finalizada
     */
    @Transactional
    public void cancelarRascunho(Long id) {
        Consulta consulta = obterRascunhoDoMedico(id);
        consultaRepository.delete(consulta);
    }

    /**
     * Analisa a transcrição com a IA e grava o resultado como conteúdo ainda revisável.
     *
     * <p>Sequência: (1) impede análises simultâneas da mesma consulta; (2) valida autoria, estado e tamanho
     * do texto; (3) se o texto e a versão do prompt não mudaram, devolve a análise anterior sem gastar tokens;
     * (4) confere a cota diária do médico; (5) chama a Gemini, que repete a chamada e troca de modelo em
     * falhas passageiras; (6) registra os tokens e grava a análise.</p>
     *
     * <p>A chamada externa ocorre fora de transação, para que a espera pela IA não mantenha conexão do banco
     * aberta. O consumo é registrado em transação própria e se preserva mesmo quando a gravação seguinte falha.</p>
     *
     * @param id identificador do rascunho a analisar
     * @return sugestões da IA e o consumo de tokens da chamada
     * @throws ApiException HTTP 400 sem transcrição ou acima do limite, 409 se já houver análise em andamento,
     *                      429 acima da cota diária ou 503 se a IA estiver indisponível
     */
    public AnaliseIaResponse analisarComIa(Long id) {
        Medico medico = medicoAtualService.obter();
        Long medicoId = medico.getId();
        Long clinicaId = medico.getClinica().getId();
        if (!emAnalise.add(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma análise em andamento para esta consulta");
        }
        try {
            Preparo preparo = transacao.execute(status -> preparar(id));
            if (preparo.anterior() != null) {
                usoIaService.registrarReaproveitada(medicoId, clinicaId, id, preparo.modeloAnterior());
                return preparo.anterior().comUso(new UsoAnaliseDto(preparo.modeloAnterior(), 0, 0, 0, 0, 0, true));
            }
            usoIaService.verificarLimiteDiario(medicoId);
            ResultadoAnaliseIa resultado = chamarGemini(preparo.transcricao(), medicoId, clinicaId, id);
            GeminiResposta resposta = resultado.resposta();
            usoIaService.registrarSucesso(medicoId, clinicaId, id, resposta);
            transacao.executeWithoutResult(
                    status -> gravarAnalise(id, preparo.hash(), resultado.analise(), resposta.modelo())
            );
            return resultado.analise().comUso(usoDe(resposta));
        } finally {
            emAnalise.remove(id);
        }
    }

    /**
     * Chama a Gemini e, se a tentativa falhar, registra a falha (com os tokens já cobrados, se houver) antes de
     * propagar o erro.
     *
     * @param transcricao texto a analisar
     * @param medicoId médico que solicitou
     * @param clinicaId clínica do médico
     * @param consultaId consulta analisada
     * @return análise estruturada e a resposta bruta, com o consumo de tokens
     */
    private ResultadoAnaliseIa chamarGemini(String transcricao, Long medicoId, Long clinicaId, Long consultaId) {
        try {
            return geminiService.analisar(transcricao);
        } catch (GeminiFalhaException falha) {
            usoIaService.registrarFalha(medicoId, clinicaId, consultaId, falha);
            throw falha;
        }
    }

    /**
     * Valida autoria, estado e tamanho do texto e procura uma análise reaproveitável.
     *
     * @param id identificador do rascunho
     * @return dados necessários à chamada externa, com a análise anterior quando o texto não mudou
     */
    private Preparo preparar(Long id) {
        Consulta consulta = obterRascunhoDoMedico(id);
        String transcricao = consulta.getTranscricaoOriginal();
        if (!temTexto(transcricao)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Inclua a transcrição antes de solicitar a análise da IA");
        }
        if (transcricao.length() > maxCaracteresEntrada) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A transcrição tem " + transcricao.length()
                    + " caracteres e o limite para análise por IA é " + maxCaracteresEntrada
                    + ". Resuma o relato ou finalize a consulta manualmente");
        }
        // A versão do prompt compõe o hash: alterar as instruções invalida análises antigas.
        String hash = sha256(GeminiService.VERSAO_DO_PROMPT + "|" + transcricao.trim());
        boolean semMudanca = hash.equals(consulta.getHashAnaliseIa()) && temTexto(consulta.getUltimaAnaliseJson());
        AnaliseIaResponse anterior = semMudanca ? lerAnaliseSalva(consulta.getUltimaAnaliseJson()) : null;
        return new Preparo(transcricao, hash, anterior, consulta.getModeloIa());
    }

    /**
     * Relê a última análise guardada para reaproveitá-la.
     *
     * @param json última análise guardada na consulta
     * @return análise reaproveitável, ou {@code null} se o conteúdo guardado não puder ser lido
     */
    private AnaliseIaResponse lerAnaliseSalva(String json) {
        try {
            return objectMapper.readValue(json, AnaliseIaResponse.class).normalizada();
        } catch (RuntimeException jsonInvalido) {
            return null;
        }
    }

    /**
     * Grava a análise no rascunho sem tocar na transcrição original.
     *
     * @param id identificador do rascunho
     * @param hash hash da versão do prompt e do texto analisado, usado no reaproveitamento
     * @param analise resultado estruturado da IA
     * @param modelo modelo que respondeu
     */
    private void gravarAnalise(Long id, String hash, AnaliseIaResponse analise, String modelo) {
        Consulta consulta = obterRascunhoDoMedico(id);
        aplicarSoap(consulta, analise.soap());
        consulta.setPreDiagnosticosSugeridos(new ArrayList<>(analise.preDiagnosticos()));
        consulta.setExamesSugeridos(new ArrayList<>(analise.exames()));
        consulta.setPrescricoesSugeridas(new ArrayList<>(analise.prescricoes()));
        consulta.setAnalisadaPorIa(true);
        consulta.setDataAnaliseIa(LocalDateTime.now(ZONE_ID));
        consulta.setModeloIa(modelo);
        consulta.setTranscricaoIdentificada(limpar(analise.transcricaoIdentificada()));
        consulta.setHashAnaliseIa(hash);
        consulta.setUltimaAnaliseJson(objectMapper.writeValueAsString(analise));
        consultaRepository.save(consulta);
    }

    /**
     * Extrai da resposta bruta o consumo exibido ao cliente.
     *
     * @param r resposta bruta da Gemini
     * @return consumo de tokens e modelo da chamada, para devolver ao cliente
     */
    private static UsoAnaliseDto usoDe(GeminiResposta r) {
        return new UsoAnaliseDto(r.modelo(), r.tokensEntrada(), r.tokensSaida(), r.tokensRaciocinio(),
                r.tokensTotal(), r.tentativas(), false);
    }

    /**
     * Calcula o resumo SHA-256 usado para detectar se a transcrição mudou.
     *
     * @param texto texto a resumir
     * @return hash SHA-256 em hexadecimal
     */
    private static String sha256(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException indisponivel) {
            throw new IllegalStateException(indisponivel);
        }
    }

    /**
     * Dados lidos antes da chamada externa, que ocorre fora de transação.
     *
     * @param transcricao texto a analisar
     * @param hash hash da versão do prompt e do texto
     * @param anterior análise reaproveitável, ou {@code null} se o texto mudou
     * @param modeloAnterior modelo que gerou a análise anterior
     */
    private record Preparo(String transcricao, String hash, AnaliseIaResponse anterior, String modeloAnterior) {
    }

    /**
     * Confirma SOAP completo, encerra a edição e atualiza o prontuário.
     *
     * @param id identificador do rascunho
     * @param request conteúdo final revisado
     * @return consulta em estado FINALIZADA
     */
    @Transactional
    public ConsultaResponse finalizar(Long id, FinalizarConsultaRequest request) {
        Consulta consulta = obterRascunhoDoMedico(id);
        validarSoapCompleto(request.soap());
        aplicarSoap(consulta, request.soap());
        consulta.setDadosGeraisConsulta(limpar(request.dadosGeraisConsulta()));
        consulta.setStatus(StatusConsulta.FINALIZADA);
        consulta.setDataFinalizacao(LocalDateTime.now(ZONE_ID));
        prontuarioService.atualizarComConsulta(consulta, request.consolidacaoProntuario());
        return toResponse(consultaRepository.save(consulta));
    }

    /**
     * Busca uma consulta respeitando o vínculo e a privacidade do rascunho.
     *
     * @param id identificador
     * @return consulta acessível ao médico atual
     */
    @Transactional(readOnly = true)
    public ConsultaResponse buscar(Long id) {
        Medico medico = medicoAtualService.obter();
        Consulta consulta = obter(id);
        if (consulta.getStatus() == StatusConsulta.RASCUNHO && !consulta.getMedico().getId().equals(medico.getId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Rascunhos só podem ser vistos pelo médico que iniciou a consulta"
            );
        }
        pacienteService.obterComAcesso(consulta.getProntuario().getPaciente().getId(), medico);
        return toResponse(consulta);
    }

    /**
     * Lista o histórico clínico do paciente.
     *
     * @param pacienteId paciente pertencente à clínica do médico
     * @return somente consultas FINALIZADAS em ordem decrescente de data
     */
    @Transactional(readOnly = true)
    public List<ConsultaResponse> historicoPaciente(Long pacienteId) {
        Medico medico = medicoAtualService.obter();
        pacienteService.obterComAcesso(pacienteId, medico);
        return consultaRepository.findByProntuarioPacienteIdAndStatusOrderByDataHoraConsultaDesc(
                pacienteId, StatusConsulta.FINALIZADA).stream().map(this::toResponse).toList();
    }

    /**
     * Lista os rascunhos do médico autenticado.
     *
     * @return rascunhos pertencentes ao médico autenticado
     */
    @Transactional(readOnly = true)
    public List<ConsultaResponse> listarMeusRascunhos() {
        Medico medico = medicoAtualService.obter();
        return consultaRepository.findByMedicoIdAndStatusOrderByDataHoraConsultaDesc(
                medico.getId(), StatusConsulta.RASCUNHO).stream().map(this::toResponse).toList();
    }

    /**
     * Valida autoria e mutabilidade antes de qualquer alteração.
     *
     * @param id identificador da consulta
     * @return rascunho pertencente ao médico atual
     */
    private Consulta obterRascunhoDoMedico(Long id) {
        Medico medico = medicoAtualService.obter();
        Consulta consulta = obter(id);
        if (!consulta.getMedico().getId().equals(medico.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Somente o médico responsável pela consulta pode alterá-la");
        }
        if (consulta.getStatus() != StatusConsulta.RASCUNHO) {
            throw new ApiException(HttpStatus.CONFLICT, "Consulta finalizada é imutável");
        }
        return consulta;
    }

    /**
     * Carrega a consulta com as relações necessárias à resposta.
     *
     * @param id identificador
     * @return consulta completa
     * @throws ApiException com HTTP 404 quando ausente
     */
    private Consulta obter(Long id) {
        return consultaRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Consulta não encontrada"));
    }

    /**
     * Exige que os quatro eixos do SOAP estejam preenchidos antes de finalizar.
     *
     * @param soap conteúdo que será consolidado
     * @throws ApiException com HTTP 400 quando algum eixo está vazio
     */
    private void validarSoapCompleto(SoapDto soap) {
        if (soap == null || !temTexto(soap.subjetivo()) || !temTexto(soap.objetivo())
                || !temTexto(soap.avaliacao()) || !temTexto(soap.plano())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Os quatro eixos SOAP devem ser revisados e preenchidos antes da finalização"
            );
        }
    }

    /**
     * Copia os quatro eixos do SOAP para a consulta, sem espaços nas pontas.
     *
     * @param consulta entidade de destino
     * @param soap valores SOAP; {@code null} mantém os valores atuais
     */
    private void aplicarSoap(Consulta consulta, SoapDto soap) {
        if (soap == null) {
            return;
        }
        consulta.setSoapSubjetivo(limpar(soap.subjetivo()));
        consulta.setSoapObjetivo(limpar(soap.objetivo()));
        consulta.setSoapAvaliacao(limpar(soap.avaliacao()));
        consulta.setSoapPlano(limpar(soap.plano()));
    }

    /**
     * Converte a consulta na resposta enviada ao cliente.
     *
     * @param consulta entidade com relações carregadas
     * @return DTO seguro para o cliente; para rascunhos analisados inclui os dados do prontuário identificados pela IA
     */
    public ConsultaResponse toResponse(Consulta consulta) {
        Paciente paciente = consulta.getProntuario().getPaciente();
        return new ConsultaResponse(
                consulta.getId(), paciente.getId(), paciente.getNomeCompleto(), consulta.getProntuario().getId(),
                consulta.getMedico().getId(), consulta.getMedico().getNomeCompleto(), consulta.getDataHoraConsulta(),
                consulta.getDataFinalizacao(), consulta.getStatus(), consulta.getTranscricaoOriginal(),
                new SoapDto(
                        consulta.getSoapSubjetivo(), consulta.getSoapObjetivo(),
                        consulta.getSoapAvaliacao(), consulta.getSoapPlano()
                ),
                consulta.getDadosGeraisConsulta(), consulta.isAnalisadaPorIa(), consulta.getDataAnaliseIa(),
                List.copyOf(consulta.getPreDiagnosticosSugeridos()), List.copyOf(consulta.getExamesSugeridos()),
                List.copyOf(consulta.getPrescricoesSugeridas()), consulta.getAlergiasConfirmadas(),
                consulta.getMedicamentosUsoContinuoConfirmados(), consulta.getHistoricoFamiliarConfirmado(),
                consulta.getTranscricaoIdentificada(), consulta.getModeloIa(), dadosIdentificados(consulta)
        );
    }

    /**
     * Recupera os dados do prontuário que a IA identificou na última análise de um rascunho.
     *
     * <p>Esses dados só existem na resposta da análise. Sem devolvê-los ao reabrir o rascunho, o painel de
     * confirmação voltaria vazio e uma alergia identificada poderia deixar de ser confirmada sem o médico perceber.
     * Consultas finalizadas não os expõem, pois o que vale é o que o médico confirmou.</p>
     *
     * @param consulta consulta a converter
     * @return dados identificados, ou {@code null} se a consulta não for rascunho ou não tiver análise legível
     */
    private ConsolidacaoProntuarioRequest dadosIdentificados(Consulta consulta) {
        if (consulta.getStatus() != StatusConsulta.RASCUNHO || !temTexto(consulta.getUltimaAnaliseJson())) {
            return null;
        }
        AnaliseIaResponse analise = lerAnaliseSalva(consulta.getUltimaAnaliseJson());
        return analise == null ? null : analise.dadosProntuarioIdentificados();
    }

    /**
     * Verifica se há conteúdo além de espaços.
     *
     * @param value texto avaliado
     * @return se contém informação não branca
     */
    private boolean temTexto(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Normaliza texto opcional: vazio vira {@code null}.
     *
     * @param value texto opcional
     * @return texto aparado ou {@code null}
     */
    private String limpar(String value) {
        return temTexto(value) ? value.trim() : null;
    }
}
