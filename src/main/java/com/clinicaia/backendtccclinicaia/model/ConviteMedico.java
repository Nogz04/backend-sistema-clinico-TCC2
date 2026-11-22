package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Convite de ativação de conta, de uso único e com prazo de validade.
 *
 * <p>Somente o hash do código enviado por e-mail é persistido, de modo que um vazamento do banco não
 * permita ativar contas.</p>
 */
@Entity
@Table(
        name = "convites_medico",
        indexes = @Index(name = "uk_convite_token_hash", columnList = "token_hash", unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class ConviteMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Médico convidado; cada médico recebe no máximo um convite. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, unique = true)
    private Medico medico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_medico_id", nullable = false)
    private Medico criadoPor;

    /** Hash SHA-256 do código enviado por e-mail. O código em si não é guardado. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    /** Momento em que o convite foi usado; nulo enquanto estiver pendente (uso único). */
    @Column(name = "utilizado_em")
    private LocalDateTime utilizadoEm;

    /**
     * Informa se o convite ainda pode ser usado: uso único, portanto precisa estar pendente e dentro do prazo.
     *
     * @param agora instante de referência
     * @return {@code true} se o convite ainda não foi usado e não expirou
     */
    public boolean estaValidoEm(LocalDateTime agora) {
        return utilizadoEm == null && expiraEm.isAfter(agora);
    }
}
