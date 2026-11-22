package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.RecuperacaoSenha;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistência de pedidos de recuperação de senha.
 */
public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha, Long> {

    /**
     * Busca o pedido pelo hash do código, carregando o usuário na mesma consulta.
     *
     * @param tokenHash hash SHA-256 do código recebido por e-mail
     * @return pedido encontrado, com o usuário carregado, ou vazio
     */
    @EntityGraph(attributePaths = "usuario")
    Optional<RecuperacaoSenha> findByTokenHash(String tokenHash);
}
