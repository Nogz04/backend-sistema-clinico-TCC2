package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse.Configuracao;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse.DiaUso;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse.MedicoUso;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoIaPainelResponse.Periodos;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoLinha;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoMedicoSoma;
import com.clinicaia.backendtccclinicaia.dto.ia.UsoTotais;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.UsoIa;
import com.clinicaia.backendtccclinicaia.repository.ClinicaRepository;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.repository.UsoIaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Registra e consolida o consumo de tokens da Gemini.
 * Os registros usam transação própria para que o gasto (e as falhas) nunca se percam por erro posterior.
 */
@Service
public class UsoIaService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    /** Data anterior a qualquer registro, usada como início da janela "todo o período". */
    private static final LocalDateTime DESDE_O_INICIO = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int DIAS_DA_SERIE = 14;

    private final UsoIaRepository repository;
    private final MedicoRepository medicoRepository;
    private final ClinicaRepository clinicaRepository;
    private final MedicoAtualService medicoAtualService;
    private final Configuracao configuracao;

    /**
     * Cria o serviço e fixa a configuração exibida no painel.
     *
     * @param repository consumo registrado
     * @param medicoRepository médicos da clínica, para o ranking
     * @param clinicaRepository clínicas, para vincular o registro
     * @param medicoAtualService identidade do médico autenticado
     * @param modeloPrincipal modelo da primeira tentativa
     * @param modeloReserva modelo das últimas tentativas; vazio repete o principal
     * @param limiteDiario tokens por médico por dia; 0 significa sem limite
     */
    public UsoIaService(
            UsoIaRepository repository,
            MedicoRepository medicoRepository,
            ClinicaRepository clinicaRepository,
            MedicoAtualService medicoAtualService,
            @Value("${gemini.model}") String modeloPrincipal,
            @Value("${gemini.fallback-model:}") String modeloReserva,
            @Value("${gemini.daily-token-limit-per-doctor:0}") long limiteDiario
    ) {
        this.repository = repository;
        this.medicoRepository = medicoRepository;
        this.clinicaRepository = clinicaRepository;
        this.medicoAtualService = medicoAtualService;
        this.configuracao = new Configuracao(
                modeloPrincipal,
                modeloReserva.isBlank() ? modeloPrincipal : modeloReserva.trim(),
                Math.max(0, limiteDiario)
        );
    }

    /**
     * Registra uma análise concluída, com os tokens cobrados pelo provedor.
     *
     * @param medicoId médico que solicitou
     * @param clinicaId clínica do médico
     * @param consultaId consulta analisada
     * @param resposta resposta da Gemini, com tokens, modelo e tentativas
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSucesso(Long medicoId, Long clinicaId, Long consultaId, GeminiResposta resposta) {
        UsoIa uso = novoRegistro(medicoId, clinicaId, consultaId);
        copiarTokens(uso, resposta);
        uso.setSucesso(true);
        repository.save(uso);
    }

    /**
     * Registra uma análise atendida com o resultado anterior: nenhum token é gasto.
     *
     * @param medicoId médico que solicitou
     * @param clinicaId clínica do médico
     * @param consultaId consulta analisada
     * @param modelo modelo que gerou o resultado reaproveitado
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarReaproveitada(Long medicoId, Long clinicaId, Long consultaId, String modelo) {
        UsoIa uso = novoRegistro(medicoId, clinicaId, consultaId);
        uso.setModelo(modelo);
        uso.setSucesso(true);
        uso.setReaproveitada(true);
        repository.save(uso);
    }

    /**
     * Registra uma tentativa que falhou. Guarda também os tokens já cobrados quando a resposta chegou, mas era inválida.
     *
     * @param medicoId médico que solicitou
     * @param clinicaId clínica do médico
     * @param consultaId consulta analisada
     * @param falha motivo da falha e, se houver, a resposta recebida
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFalha(Long medicoId, Long clinicaId, Long consultaId, GeminiFalhaException falha) {
        UsoIa uso = novoRegistro(medicoId, clinicaId, consultaId);
        if (falha.getRespostaRecebida() != null) {
            copiarTokens(uso, falha.getRespostaRecebida());
        } else {
            uso.setModelo(falha.getModelo());
            uso.setTentativas(falha.getTentativas());
            uso.setDuracaoMs(falha.getDuracaoMs());
        }
        uso.setMensagemErro(abreviar(falha.getMessage()));
        repository.save(uso);
    }

    /**
     * Impede nova análise quando o médico já gastou a cota diária de tokens.
     *
     * @param medicoId médico que solicita a análise
     * @throws ApiException com HTTP 429 quando a cota diária foi atingida (0 = sem limite)
     */
    @Transactional(readOnly = true)
    public void verificarLimiteDiario(Long medicoId) {
        long limite = configuracao.limiteDiarioPorMedico();
        if (limite > 0 && repository.totaisDoMedico(medicoId, inicioDoDia()).tokensTotal() >= limite) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Limite diário de " + limite + " tokens de IA atingido. Finalize a consulta manualmente ou tente amanhã"
            );
        }
    }

    /**
     * Monta o painel de consumo de tokens do médico autenticado e da sua clínica.
     *
     * <p>Reúne os totais de hoje, do mês corrente (a partir do dia 1) e de todo o período, o consumo de cada médico
     * da clínica e a série diária dos últimos 14 dias. Somente números são devolvidos: nenhum conteúdo clínico.</p>
     *
     * @return painel de consumo
     */
    @Transactional(readOnly = true)
    public UsoIaPainelResponse painel() {
        Medico eu = medicoAtualService.obter();
        Long clinicaId = eu.getClinica().getId();
        return new UsoIaPainelResponse(
                configuracao,
                periodos(desde -> repository.totaisDoMedico(eu.getId(), desde)),
                periodos(desde -> repository.totaisDaClinica(clinicaId, desde)),
                ranking(clinicaId, eu.getId()),
                serieDiaria(clinicaId, eu.getId()));
    }

    /**
     * Calcula os totais de hoje, do mês corrente (a partir do dia 1) e de todo o período.
     *
     * @param totaisDesde consulta de totais parametrizada pelo início da janela
     * @return totais das três janelas
     */
    private Periodos periodos(Function<LocalDateTime, UsoTotais> totaisDesde) {
        LocalDateTime hoje = inicioDoDia();
        return new Periodos(
                totaisDesde.apply(hoje),
                totaisDesde.apply(hoje.withDayOfMonth(1)),
                totaisDesde.apply(DESDE_O_INICIO)
        );
    }

    /**
     * Monta o consumo de cada médico da clínica. Médicos que nunca usaram a IA aparecem com zeros.
     *
     * @param clinicaId clínica do médico autenticado
     * @param meuId identificador do médico autenticado, para marcá-lo na lista
     * @return uma linha por médico, em ordem alfabética
     */
    private List<MedicoUso> ranking(Long clinicaId, Long meuId) {
        Map<Long, UsoMedicoSoma> noMes = somasPorMedico(clinicaId, inicioDoDia().withDayOfMonth(1));
        Map<Long, UsoMedicoSoma> total = somasPorMedico(clinicaId, DESDE_O_INICIO);
        return medicoRepository.findByClinicaIdOrderByNomeCompletoAsc(clinicaId).stream()
                .map(medico -> {
                    UsoMedicoSoma mes = noMes.get(medico.getId());
                    UsoMedicoSoma tudo = total.get(medico.getId());
                    return new MedicoUso(medico.getId(), medico.getNomeCompleto(), medico.getCrm(),
                            mes == null ? 0 : mes.tokensTotal(),
                            tudo == null ? 0 : tudo.tokensTotal(),
                            tudo == null ? 0 : tudo.solicitacoes(),
                            tudo == null ? null : tudo.ultimoUso(),
                            medico.getId().equals(meuId));
                })
                .toList();
    }

    /**
     * Agrupa o consumo por médico.
     *
     * @param clinicaId clínica consultada
     * @param desde início da janela de tempo
     * @return soma de uso por identificador de médico a partir do instante informado
     */
    private Map<Long, UsoMedicoSoma> somasPorMedico(Long clinicaId, LocalDateTime desde) {
        return repository.totaisPorMedico(clinicaId, desde).stream()
                .collect(Collectors.toMap(UsoMedicoSoma::medicoId, soma -> soma));
    }

    /**
     * Monta a série dos últimos {@value #DIAS_DA_SERIE} dias, com o consumo do médico autenticado e o da clínica
     * em cada dia. Dias sem uso aparecem com zero.
     *
     * @param clinicaId clínica do médico autenticado
     * @param meuId identificador do médico autenticado
     * @return um item por dia, do mais antigo ao mais recente
     */
    private List<DiaUso> serieDiaria(Long clinicaId, Long meuId) {
        LocalDate primeiro = LocalDate.now(ZONE_ID).minusDays(DIAS_DA_SERIE - 1L);
        List<UsoLinha> linhas = repository.linhasDaClinica(clinicaId, primeiro.atStartOfDay());
        return IntStream.range(0, DIAS_DA_SERIE)
                .mapToObj(primeiro::plusDays)
                .map(dia -> {
                    List<UsoLinha> doDia = linhas.stream()
                            .filter(l -> l.dataHora().toLocalDate().equals(dia))
                            .toList();
                    return new DiaUso(dia, somar(doDia, meuId), somar(doDia, null));
                })
                .toList();
    }

    /**
     * Soma os tokens das linhas.
     *
     * @param linhas solicitações do período
     * @param medicoId médico a somar; nulo soma todas as linhas
     * @return total de tokens
     */
    private long somar(List<UsoLinha> linhas, Long medicoId) {
        return linhas.stream()
                .filter(linha -> medicoId == null || medicoId.equals(linha.medicoId()))
                .mapToLong(UsoLinha::tokensTotal)
                .sum();
    }

    /**
     * Cria o registro de uso com os campos comuns a sucesso, reaproveitamento e falha.
     *
     * @param medicoId médico que solicitou
     * @param clinicaId clínica do médico
     * @param consultaId consulta analisada
     * @return registro inicial do uso, com médico, clínica, consulta e momento preenchidos
     */
    private UsoIa novoRegistro(Long medicoId, Long clinicaId, Long consultaId) {
        UsoIa uso = new UsoIa();
        uso.setMedico(medicoRepository.getReferenceById(medicoId));
        uso.setClinica(clinicaRepository.getReferenceById(clinicaId));
        uso.setConsultaId(consultaId);
        uso.setDataHora(LocalDateTime.now(ZONE_ID));
        return uso;
    }

    /**
     * Copia para o registro os tokens, o modelo, as tentativas e a duração informados pelo provedor.
     *
     * @param uso registro de destino
     * @param resposta resposta da Gemini
     */
    private void copiarTokens(UsoIa uso, GeminiResposta resposta) {
        uso.setModelo(resposta.modelo());
        uso.setTokensEntrada(resposta.tokensEntrada());
        uso.setTokensSaida(resposta.tokensSaida());
        uso.setTokensRaciocinio(resposta.tokensRaciocinio());
        uso.setTokensTotal(resposta.tokensTotal());
        uso.setTentativas(resposta.tentativas());
        uso.setDuracaoMs(resposta.duracaoMs());
    }

    /**
     * Ajusta a mensagem de erro ao tamanho da coluna.
     *
     * @param mensagem mensagem original, possivelmente nula
     * @return mensagem limitada a 255 caracteres, tamanho da coluna
     */
    private String abreviar(String mensagem) {
        return mensagem != null && mensagem.length() > 255 ? mensagem.substring(0, 255) : mensagem;
    }

    /**
     * Referência das janelas "hoje" e "mês" do painel.
     *
     * @return início do dia atual no fuso de São Paulo
     */
    private LocalDateTime inicioDoDia() {
        return LocalDate.now(ZONE_ID).atStartOfDay();
    }
}
