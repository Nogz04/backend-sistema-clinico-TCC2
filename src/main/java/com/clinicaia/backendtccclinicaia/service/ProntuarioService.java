package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.prontuario.ConsolidacaoProntuarioRequest;
import com.clinicaia.backendtccclinicaia.dto.prontuario.ProntuarioResponse;
import com.clinicaia.backendtccclinicaia.dto.prontuario.AtualizacaoProntuarioResponse;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Consulta;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.Prontuario;
import com.clinicaia.backendtccclinicaia.model.StatusConsulta;
import com.clinicaia.backendtccclinicaia.model.AtualizacaoProntuario;
import com.clinicaia.backendtccclinicaia.repository.AtualizacaoProntuarioRepository;
import com.clinicaia.backendtccclinicaia.repository.ConsultaRepository;
import com.clinicaia.backendtccclinicaia.repository.ProntuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Consulta e atualização do prontuário único do paciente.
 *
 * <p>O prontuário guarda o estado clínico atual. Cada alteração confirmada na finalização de uma consulta
 * gera um registro de auditoria ({@code AtualizacaoProntuario}) com o valor anterior, o novo, a consulta de
 * origem e o médico responsável.</p>
 */
@Service
public class ProntuarioService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final ProntuarioRepository prontuarioRepository;
    private final ConsultaRepository consultaRepository;
    private final MedicoAtualService medicoAtualService;
    private final PacienteService pacienteService;
    private final AtualizacaoProntuarioRepository atualizacaoRepository;

    /**
     * Cria o serviço com suas dependências.
     *
     * @param prontuarioRepository persistência longitudinal
     * @param consultaRepository contagem do histórico confirmado
     * @param medicoAtualService identidade do solicitante
     * @param pacienteService autorização pelo vínculo clínico
     * @param atualizacaoRepository trilha de auditoria das alterações
     */
    public ProntuarioService(
            ProntuarioRepository prontuarioRepository,
            ConsultaRepository consultaRepository,
            MedicoAtualService medicoAtualService,
            PacienteService pacienteService,
            AtualizacaoProntuarioRepository atualizacaoRepository
    ) {
        this.prontuarioRepository = prontuarioRepository;
        this.consultaRepository = consultaRepository;
        this.medicoAtualService = medicoAtualService;
        this.pacienteService = pacienteService;
        this.atualizacaoRepository = atualizacaoRepository;
    }

    /**
     * Consulta o prontuário de um paciente da clínica do médico autenticado.
     *
     * @param pacienteId proprietário do prontuário
     * @return visão longitudinal somente quando há vínculo médico
     */
    @Transactional(readOnly = true)
    public ProntuarioResponse buscarPorPaciente(Long pacienteId) {
        Medico medico = medicoAtualService.obter();
        pacienteService.obterComAcesso(pacienteId, medico);
        return toResponse(obterPorPaciente(pacienteId));
    }

    /**
     * Atualiza o prontuário com os dados confirmados pelo médico na finalização da consulta.
     *
     * <p>Campos em branco não apagam informações anteriores, e só há alteração (e auditoria) quando o valor
     * informado é diferente do atual. A atualização ocorre somente por comando explícito de finalização,
     * nunca diretamente a partir de uma sugestão da IA.</p>
     *
     * @param consulta consulta que está sendo finalizada
     * @param dados valores revisados pelo médico; campos vazios não apagam o prontuário
     */
    @Transactional
    public void atualizarComConsulta(Consulta consulta, ConsolidacaoProntuarioRequest dados) {
        Prontuario prontuario = consulta.getProntuario();
        if (dados != null) {
            consulta.setAlergiasConfirmadas(limpar(dados.alergiasConhecidas()));
            consulta.setMedicamentosUsoContinuoConfirmados(limpar(dados.medicamentosUsoContinuo()));
            consulta.setHistoricoFamiliarConfirmado(limpar(dados.historicoFamiliar()));
            atualizarCampo(
                    prontuario, consulta, "Alergias conhecidas",
                    prontuario.getAlergiasConhecidas(), dados.alergiasConhecidas(), prontuario::setAlergiasConhecidas
            );
            atualizarCampo(
                    prontuario, consulta, "Medicamentos de uso contínuo",
                    prontuario.getMedicamentosUsoContinuo(), dados.medicamentosUsoContinuo(), prontuario::setMedicamentosUsoContinuo
            );
            atualizarCampo(
                    prontuario, consulta, "Histórico familiar",
                    prontuario.getHistoricoFamiliar(), dados.historicoFamiliar(), prontuario::setHistoricoFamiliar
            );
            atualizarCampo(
                    prontuario, consulta, "Resumo clínico",
                    prontuario.getResumoClinico(), dados.resumoClinico(), prontuario::setResumoClinico
            );
            atualizarCampo(
                    prontuario, consulta, "Antecedentes pessoais",
                    prontuario.getAntecedentesPessoais(), dados.antecedentesPessoais(), prontuario::setAntecedentesPessoais
            );
            atualizarCampo(
                    prontuario, consulta, "Condições e problemas ativos",
                    prontuario.getCondicoesAtivas(), dados.condicoesAtivas(), prontuario::setCondicoesAtivas
            );
            atualizarCampo(
                    prontuario, consulta, "Cirurgias e internações",
                    prontuario.getCirurgiasInternacoes(), dados.cirurgiasInternacoes(), prontuario::setCirurgiasInternacoes
            );
            atualizarCampo(
                    prontuario, consulta, "Imunizações",
                    prontuario.getImunizacoes(), dados.imunizacoes(), prontuario::setImunizacoes
            );
            atualizarCampo(
                    prontuario, consulta, "Hábitos e aspectos sociais",
                    prontuario.getHabitosAspectosSociais(), dados.habitosAspectosSociais(), prontuario::setHabitosAspectosSociais
            );
            atualizarCampo(
                    prontuario, consulta, "Diagnósticos confirmados",
                    prontuario.getDiagnosticosConfirmados(), dados.diagnosticosConfirmados(), prontuario::setDiagnosticosConfirmados
            );
            atualizarCampo(
                    prontuario, consulta, "Exames relevantes",
                    prontuario.getExamesRelevantes(), dados.examesRelevantes(), prontuario::setExamesRelevantes
            );
            atualizarCampo(
                    prontuario, consulta, "Tratamentos e procedimentos",
                    prontuario.getTratamentosProcedimentos(), dados.tratamentosProcedimentos(), prontuario::setTratamentosProcedimentos
            );
            atualizarCampo(
                    prontuario, consulta, "Plano terapêutico atual",
                    prontuario.getPlanoTerapeuticoAtual(), dados.planoTerapeuticoAtual(), prontuario::setPlanoTerapeuticoAtual
            );
            atualizarCampo(
                    prontuario, consulta, "Acompanhamento e pendências",
                    prontuario.getAcompanhamentoPendencias(), dados.acompanhamentoPendencias(), prontuario::setAcompanhamentoPendencias
            );
        }
        prontuario.setDataUltimaAtualizacao(LocalDateTime.now(ZONE_ID));
        prontuarioRepository.save(prontuario);
    }

    /**
     * Grava um campo do prontuário e registra a auditoria, quando o valor realmente muda.
     *
     * @param prontuario prontuário de destino
     * @param consulta consulta de origem da alteração
     * @param campo nome legível do campo, usado na trilha de auditoria
     * @param anterior valor atual do campo
     * @param informado valor confirmado pelo médico; vazio ou igual ao atual não gera alteração
     * @param setter função que grava o novo valor no prontuário
     */
    private void atualizarCampo(
            Prontuario prontuario, Consulta consulta, String campo,
            String anterior, String informado, Consumer<String> setter
    ) {
        String novo = limpar(informado);
        if (novo == null || Objects.equals(limpar(anterior), novo)) return;
        setter.accept(novo);
        AtualizacaoProntuario atualizacao = new AtualizacaoProntuario();
        atualizacao.setProntuario(prontuario);
        atualizacao.setConsulta(consulta);
        atualizacao.setMedico(consulta.getMedico());
        atualizacao.setCampo(campo);
        atualizacao.setValorAnterior(limpar(anterior));
        atualizacao.setValorNovo(novo);
        atualizacao.setDataAlteracao(LocalDateTime.now(ZONE_ID));
        atualizacaoRepository.save(atualizacao);
    }

    /**
     * Carrega o prontuário único de um paciente, sem checar acesso (use {@link #buscarPorPaciente} para isso).
     *
     * @param pacienteId identificador do proprietário
     * @return entidade do prontuário único
     * @throws ApiException com HTTP 404 quando a integridade esperada não existe
     */
    public Prontuario obterPorPaciente(Long pacienteId) {
        return prontuarioRepository.findByPacienteId(pacienteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Prontuário único do paciente não encontrado"));
    }

    /**
     * Converte o prontuário na resposta enviada ao cliente, com a trilha de auditoria.
     *
     * @param prontuario entidade carregada
     * @return visão longitudinal com total de consultas finalizadas
     */
    public ProntuarioResponse toResponse(Prontuario prontuario) {
        long total = consultaRepository.countByProntuarioIdAndStatus(prontuario.getId(), StatusConsulta.FINALIZADA);
        return new ProntuarioResponse(
                prontuario.getId(), prontuario.getPaciente().getId(), prontuario.getPaciente().getNomeCompleto(),
                prontuario.getDataCriacao(), prontuario.getDataUltimaAtualizacao(), prontuario.getAlergiasConhecidas(),
                prontuario.getMedicamentosUsoContinuo(), prontuario.getHistoricoFamiliar(),
                prontuario.getResumoClinico(), prontuario.getAntecedentesPessoais(), prontuario.getCondicoesAtivas(),
                prontuario.getCirurgiasInternacoes(), prontuario.getImunizacoes(), prontuario.getHabitosAspectosSociais(),
                prontuario.getDiagnosticosConfirmados(), prontuario.getExamesRelevantes(),
                prontuario.getTratamentosProcedimentos(), prontuario.getPlanoTerapeuticoAtual(),
                prontuario.getAcompanhamentoPendencias(), total,
                atualizacaoRepository.findByProntuarioIdOrderByDataAlteracaoDesc(prontuario.getId()).stream()
                        .map(a -> new AtualizacaoProntuarioResponse(a.getId(), a.getCampo(), a.getValorAnterior(),
                                a.getValorNovo(), a.getConsulta().getId(), a.getMedico().getId(),
                                a.getMedico().getNomeCompleto(), a.getDataAlteracao())).toList()
        );
    }

    /**
     * Verifica se há conteúdo além de espaços.
     *
     * @param value texto avaliado
     * @return se contém caracteres não brancos
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
