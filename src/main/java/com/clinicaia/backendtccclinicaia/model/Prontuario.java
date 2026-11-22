package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Prontuário único e longitudinal do paciente: o estado clínico atual, acumulado ao longo das consultas.
 *
 * <p>Cada paciente tem um só prontuário, criado junto com o cadastro. Os campos guardam o valor vigente e só
 * são alterados por dados que o médico confirma ao finalizar uma consulta. O histórico de cada alteração fica em
 * {@code AtualizacaoProntuario}.</p>
 */
@Entity
@Table(name = "prontuarios")
@Getter
@Setter
@NoArgsConstructor
public class Prontuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Paciente dono do prontuário (relação um para um, única). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, unique = true)
    private Paciente paciente;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Última vez em que uma consulta finalizada atualizou o prontuário. */
    @Column(name = "data_ultima_atualizacao", nullable = false)
    private LocalDateTime dataUltimaAtualizacao;

    @Lob
    @Column(name = "alergias_conhecidas", columnDefinition = "TEXT")
    private String alergiasConhecidas;

    @Lob
    @Column(name = "medicamentos_uso_continuo", columnDefinition = "TEXT")
    private String medicamentosUsoContinuo;

    @Lob
    @Column(name = "historico_familiar", columnDefinition = "TEXT")
    private String historicoFamiliar;

    /** Visão geral resumida do quadro do paciente. */
    @Lob
    @Column(name = "resumo_clinico", columnDefinition = "TEXT")
    private String resumoClinico;

    /** Doenças e eventos de saúde anteriores do paciente. */
    @Lob
    @Column(name = "antecedentes_pessoais", columnDefinition = "TEXT")
    private String antecedentesPessoais;

    /** Problemas de saúde em curso, crônicos ou atuais. */
    @Lob
    @Column(name = "condicoes_ativas", columnDefinition = "TEXT")
    private String condicoesAtivas;

    @Lob
    @Column(name = "cirurgias_internacoes", columnDefinition = "TEXT")
    private String cirurgiasInternacoes;

    @Lob
    @Column(name = "imunizacoes", columnDefinition = "TEXT")
    private String imunizacoes;

    /** Hábitos (tabagismo, álcool, atividade física) e contexto social. */
    @Lob
    @Column(name = "habitos_aspectos_sociais", columnDefinition = "TEXT")
    private String habitosAspectosSociais;

    /** Diagnósticos confirmados pelo médico, distintos dos pré-diagnósticos sugeridos pela IA. */
    @Lob
    @Column(name = "diagnosticos_confirmados", columnDefinition = "TEXT")
    private String diagnosticosConfirmados;

    /** Resultados de exames importantes para o acompanhamento. */
    @Lob
    @Column(name = "exames_relevantes", columnDefinition = "TEXT")
    private String examesRelevantes;

    @Lob
    @Column(name = "tratamentos_procedimentos", columnDefinition = "TEXT")
    private String tratamentosProcedimentos;

    /** Conduta terapêutica vigente. */
    @Lob
    @Column(name = "plano_terapeutico_atual", columnDefinition = "TEXT")
    private String planoTerapeuticoAtual;

    /** Retornos e pendências a acompanhar. */
    @Lob
    @Column(name = "acompanhamento_pendencias", columnDefinition = "TEXT")
    private String acompanhamentoPendencias;

    /** Versão para controle de concorrência otimista. */
    @Version
    private long versao;
}
