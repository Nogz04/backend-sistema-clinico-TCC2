package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Registro imutável de uma mudança confirmada no prontuário longitudinal. */
@Entity
@Table(name = "atualizacoes_prontuario")
@Getter
@Setter
@NoArgsConstructor
public class AtualizacaoProntuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prontuario_id", nullable = false)
    private Prontuario prontuario;

    /** Consulta cuja finalização originou a alteração. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consulta_id", nullable = false)
    private Consulta consulta;

    /** Médico que confirmou a alteração. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    /** Nome legível do campo alterado (ex.: "Alergias conhecidas"). */
    @Column(nullable = false, length = 80)
    private String campo;

    /** Valor antes da alteração; nulo na primeira vez em que o campo foi preenchido. */
    @Lob
    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Lob
    @Column(name = "valor_novo", nullable = false, columnDefinition = "TEXT")
    private String valorNovo;

    /** Momento da alteração. Não pode ser atualizado depois de gravado. */
    @Column(name = "data_alteracao", nullable = false, updatable = false)
    private LocalDateTime dataAlteracao;
}
