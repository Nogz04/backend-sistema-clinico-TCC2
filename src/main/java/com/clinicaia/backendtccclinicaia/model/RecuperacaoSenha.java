package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Pedido de recuperação de senha de uma conta já ativada, de uso único e com prazo de validade.
 *
 * <p>Somente o hash do código enviado por e-mail é persistido.</p>
 */
@Entity
@Table(
        name = "recuperacoes_senha",
        indexes = @Index(name = "uk_recuperacao_token_hash", columnList = "token_hash", unique = true)
)
@Getter
@Setter
@NoArgsConstructor
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Hash SHA-256 do código enviado por e-mail. O código em si não é guardado. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    /** Momento em que o pedido foi usado; nulo enquanto estiver pendente (uso único). */
    @Column(name = "utilizado_em")
    private LocalDateTime utilizadoEm;

    /**
     * Informa se o pedido ainda pode ser usado: uso único, portanto precisa estar pendente e dentro do prazo.
     *
     * @param agora instante de referência
     * @return {@code true} se o link ainda não foi usado e não expirou
     */
    public boolean estaValidoEm(LocalDateTime agora) {
        return utilizadoEm == null && expiraEm.isAfter(agora);
    }
}
