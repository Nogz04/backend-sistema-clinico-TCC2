package com.clinicaia.backendtccclinicaia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Conta de acesso ao sistema, base da hierarquia de usuários.
 *
 * <p>É abstrata e implementa {@link UserDetails} para o Spring Security. Todo usuário atual é um {@link Medico}.</p>
 */
@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    /** E-mail, usado como login e único no sistema. */
    @Column(nullable = false, unique = true, length = 180)
    private String email;

    /** Hash BCrypt da senha. A senha nunca é guardada em texto puro. */
    @Column(nullable = false, length = 100)
    private String senha;

    /** Indica se o usuário já definiu a própria senha; falso para médico convidado que ainda não ativou a conta. */
    @Column(name = "senha_definida", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean senhaDefinida = true;

    /**
     * Concede o papel de acesso do usuário; todos os usuários atuais são médicos.
     *
     * @return autoridades do usuário (apenas {@code ROLE_MEDICO})
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_MEDICO"));
    }

    /**
     * Fornece ao Spring Security o hash da senha usado na autenticação.
     *
     * @return hash BCrypt da senha
     */
    @Override
    public String getPassword() {
        return senha;
    }

    /**
     * O e-mail faz o papel de nome de usuário no login.
     *
     * @return e-mail do usuário
     */
    @Override
    public String getUsername() {
        return email;
    }
}
