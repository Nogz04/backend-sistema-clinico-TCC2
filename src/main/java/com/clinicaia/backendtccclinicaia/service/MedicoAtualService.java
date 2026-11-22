package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Obtém o médico autenticado na requisição corrente.
 */
@Service
public class MedicoAtualService {

    private final MedicoRepository repository;

    /**
     * Cria o serviço com o repositório de médicos.
     *
     * @param repository busca do médico pelo e-mail do token
     */
    public MedicoAtualService(MedicoRepository repository) {
        this.repository = repository;
    }

    /**
     * Identifica o médico a partir do contexto de segurança da requisição.
     *
     * @return médico dono do token da requisição
     * @throws ApiException HTTP 401 se não houver autenticação ou o médico não existir mais
     */
    public Medico obter() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }
        return repository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Médico autenticado não foi encontrado"));
    }
}
