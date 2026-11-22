package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de cada solicitação de análise enviada (ou reaproveitada) pela integração com a Gemini.
 *
 * <p>Serve de base para o painel de consumo de tokens por médico e por clínica. O conteúdo clínico
 * nunca é copiado para esta tabela: guarda-se apenas quem solicitou, quando, qual modelo respondeu
 * e quantos tokens foram contabilizados pelo provedor. O vínculo com a consulta é um identificador
 * simples, sem chave estrangeira, para que o histórico de consumo sobreviva ao cancelamento de um rascunho.</p>
 */
@Entity
@Table(name = "uso_ia", indexes = {
        @Index(name = "idx_uso_ia_medico_data", columnList = "medico_id, data_hora"),
        @Index(name = "idx_uso_ia_clinica_data", columnList = "clinica_id, data_hora")
})
@Getter
@Setter
@NoArgsConstructor
public class UsoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    /** Clínica do médico, para somar o consumo por clínica. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    /** Consulta analisada. Não é chave estrangeira: o histórico de consumo sobrevive ao cancelamento do rascunho. */
    @Column(name = "consulta_id")
    private Long consultaId;

    /** Modelo que respondeu (principal ou reserva). */
    @Column(length = 80)
    private String modelo;

    /** Tokens do texto enviado (instruções e transcrição), conforme o provedor. */
    @Column(name = "tokens_entrada", nullable = false)
    private long tokensEntrada;

    /** Tokens da resposta visível, conforme o provedor. */
    @Column(name = "tokens_saida", nullable = false)
    private long tokensSaida;

    /** Tokens do raciocínio interno do modelo, conforme o provedor. */
    @Column(name = "tokens_raciocinio", nullable = false)
    private long tokensRaciocinio;

    /** Total cobrado na chamada; é o valor somado nos painéis. */
    @Column(name = "tokens_total", nullable = false)
    private long tokensTotal;

    /** Chamadas HTTP necessárias até a resposta; zero quando a análise foi reaproveitada. */
    @Column(nullable = false)
    private int tentativas;

    /** Tempo total em milissegundos, incluindo as esperas entre tentativas. */
    @Column(name = "duracao_ms", nullable = false)
    private long duracaoMs;

    /** {@code false} quando a Gemini não devolveu uma análise utilizável. */
    @Column(nullable = false)
    private boolean sucesso;

    /** {@code true} quando a análise anterior foi reutilizada e nenhum token foi gasto. */
    @Column(nullable = false)
    private boolean reaproveitada;

    @Column(name = "mensagem_erro", length = 255)
    private String mensagemErro;

    /** Momento da solicitação; base dos totais do dia, do mês e da série diária. */
    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;
}
