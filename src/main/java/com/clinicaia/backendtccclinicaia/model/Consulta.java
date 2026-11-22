package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Atendimento individual, pertencente a um prontuário e a um médico autor.
 *
 * <p>Guarda a transcrição, o registro SOAP e, separadamente, as sugestões da IA, de modo que uma sugestão
 * nunca se confunda com uma decisão já confirmada. A consulta nasce como {@code RASCUNHO} e passa a
 * {@code FINALIZADA}, estado imutável, quando o médico confirma os quatro eixos do SOAP. Os campos de dados
 * confirmados (alergias, medicamentos e histórico familiar) registram o que o médico validou nesta consulta
 * para atualizar o prontuário.</p>
 */
@Entity
@Table(name = "consultas")
@Getter
@Setter
@NoArgsConstructor
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prontuário único do paciente ao qual a consulta pertence. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prontuario_id", nullable = false)
    private Prontuario prontuario;

    /** Médico autor; só ele vê e altera o rascunho. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    /** Momento em que o atendimento foi iniciado. Não é alterado depois. */
    @Column(name = "data_hora_consulta", nullable = false, updatable = false)
    private LocalDateTime dataHoraConsulta;

    /** Momento em que o médico finalizou a consulta; nulo enquanto for rascunho. */
    @Column(name = "data_finalizacao")
    private LocalDateTime dataFinalizacao;

    /** Estado do atendimento: editável (rascunho) ou confirmado e imutável (finalizada). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConsulta status;

    /** Texto da consulta, ditado ou digitado, exatamente como informado. Nunca é sobrescrito pela IA. */
    @Lob
    @Column(name = "transcricao_original", columnDefinition = "TEXT")
    private String transcricaoOriginal;

    /** Eixo S do SOAP: queixas e sintomas relatados pelo paciente. */
    @Lob
    @Column(name = "soap_subjetivo", columnDefinition = "TEXT")
    private String soapSubjetivo;

    /** Eixo O do SOAP: dados observáveis e exame físico. */
    @Lob
    @Column(name = "soap_objetivo", columnDefinition = "TEXT")
    private String soapObjetivo;

    /** Eixo A do SOAP: avaliação e hipótese diagnóstica. */
    @Lob
    @Column(name = "soap_avaliacao", columnDefinition = "TEXT")
    private String soapAvaliacao;

    /** Eixo P do SOAP: conduta, exames e prescrições propostos. */
    @Lob
    @Column(name = "soap_plano", columnDefinition = "TEXT")
    private String soapPlano;

    /** Observações gerais do atendimento, fora dos quatro eixos. */
    @Lob
    @Column(name = "dados_gerais_consulta", columnDefinition = "TEXT")
    private String dadosGeraisConsulta;

    /** Hipóteses diagnósticas sugeridas pela IA, em ordem. Ficam separadas do SOAP até o médico aceitá-las. */
    @ElementCollection
    @CollectionTable(name = "consulta_pre_diagnosticos_ia", joinColumns = @JoinColumn(name = "consulta_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "pre_diagnostico", columnDefinition = "TEXT")
    private List<String> preDiagnosticosSugeridos = new ArrayList<>();

    /** Exames sugeridos pela IA, em ordem. */
    @ElementCollection
    @CollectionTable(name = "consulta_exames_ia", joinColumns = @JoinColumn(name = "consulta_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "exame", columnDefinition = "TEXT")
    private List<String> examesSugeridos = new ArrayList<>();

    /** Condutas ou prescrições sugeridas pela IA, em ordem. */
    @ElementCollection
    @CollectionTable(name = "consulta_prescricoes_ia", joinColumns = @JoinColumn(name = "consulta_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "prescricao", columnDefinition = "TEXT")
    private List<String> prescricoesSugeridas = new ArrayList<>();

    /** Indica se a IA foi usada nesta consulta (o uso é opcional). */
    @Column(name = "analisada_por_ia", nullable = false)
    private boolean analisadaPorIa;

    @Column(name = "data_analise_ia")
    private LocalDateTime dataAnaliseIa;

    /** Modelo que gerou a última análise (principal ou reserva), para auditoria. */
    @Column(name = "modelo_ia", length = 80)
    private String modeloIa;

    /**
     * Diálogo com as falas atribuídas a médico e paciente pela IA.
     * É uma estimativa e não substitui a transcrição original.
     */
    @Lob
    @Column(name = "transcricao_identificada", columnDefinition = "TEXT")
    private String transcricaoIdentificada;

    /**
     * Hash SHA-256 da versão do prompt e do texto analisado.
     * Se não mudou, uma nova solicitação reaproveita a análise e não gasta tokens.
     */
    @Column(name = "hash_analise_ia", length = 64)
    private String hashAnaliseIa;

    /** Resposta completa da última análise, guardada para o reaproveitamento. */
    @Lob
    @Column(name = "ultima_analise_json", columnDefinition = "TEXT")
    private String ultimaAnaliseJson;

    /** Alergias confirmadas pelo médico nesta consulta. */
    @Lob
    @Column(name = "alergias_confirmadas", columnDefinition = "TEXT")
    private String alergiasConfirmadas;

    /** Medicamentos de uso contínuo confirmados pelo médico nesta consulta. */
    @Lob
    @Column(name = "medicamentos_uso_continuo_confirmados", columnDefinition = "TEXT")
    private String medicamentosUsoContinuoConfirmados;

    /** Histórico familiar confirmado pelo médico nesta consulta. */
    @Lob
    @Column(name = "historico_familiar_confirmado", columnDefinition = "TEXT")
    private String historicoFamiliarConfirmado;

    /** Controle de concorrência otimista: impede que duas edições simultâneas se sobrescrevam em silêncio. */
    @Version
    private long versao;
}
