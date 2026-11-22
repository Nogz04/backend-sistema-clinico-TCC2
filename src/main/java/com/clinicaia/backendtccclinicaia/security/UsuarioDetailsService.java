package com.clinicaia.backendtccclinicaia.security;

import com.clinicaia.backendtccclinicaia.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário pelo e-mail para o Spring Security.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repository;

    /**
     * Cria o serviço com o repositório de usuários.
     *
     * @param repository acesso aos usuários cadastrados
     */
    public UsuarioDetailsService(UsuarioRepository repository) {
        this.repository = repository;
    }

    /**
     * Busca o usuário ignorando diferença entre maiúsculas e minúsculas.
     *
     * @param username e-mail do usuário
     * @return usuário encontrado
     * @throws UsernameNotFoundException quando não há usuário com o e-mail informado
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
