package com.clinicaia.backendtccclinicaia.repository;

import com.clinicaia.backendtccclinicaia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistência de usuários.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca o usuário pelo e-mail, que é o login.
     *
     * @param email e-mail do usuário, sem diferenciar maiúsculas de minúsculas
     * @return usuário encontrado, ou vazio
     */
    Optional<Usuario> findByEmailIgnoreCase(String email);

    /**
     * Verifica se o e-mail já está em uso.
     *
     * @param email e-mail a verificar
     * @return {@code true} se já houver usuário com o e-mail
     */
    boolean existsByEmailIgnoreCase(String email);
}
