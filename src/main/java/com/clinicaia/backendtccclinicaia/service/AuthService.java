package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.auth.LoginRequest;
import com.clinicaia.backendtccclinicaia.dto.auth.TokenResponse;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.MedicoRepository;
import com.clinicaia.backendtccclinicaia.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticação de médicos por e-mail e senha.
 */
@Service
public class AuthService {

    private final MedicoRepository medicoRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Cria o serviço com suas dependências.
     *
     * @param medicoRepository busca do médico pelo e-mail
     * @param passwordEncoder confere a senha contra o hash BCrypt
     * @param tokenService emissão do JWT
     */
    public AuthService(MedicoRepository medicoRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.medicoRepository = medicoRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /**
     * Valida as credenciais e emite o token de acesso.
     *
     * <p>E-mail inexistente e senha incorreta recebem a mesma resposta, para não revelar quais contas existem.
     * Um médico convidado que ainda não definiu a senha é orientado a usar o link do convite.</p>
     *
     * @param request e-mail e senha
     * @return token de acesso e dados básicos do médico
     * @throws ApiException HTTP 401 para credenciais inválidas ou HTTP 403 se a senha ainda não foi definida
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Medico medico = medicoRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(this::credenciaisInvalidas);
        if (!medico.isSenhaDefinida()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "Defina sua senha pelo link de convite antes do primeiro acesso"
            );
        }
        if (!passwordEncoder.matches(request.senha(), medico.getSenha())) {
            throw credenciaisInvalidas();
        }
        TokenService.TokenGerado token = tokenService.gerar(medico);
        return new TokenResponse(
                token.token(), "Bearer", token.expiraEm(), medico.getId(), medico.getNomeCompleto(), medico.getClinicaAtuacao()
        );
    }

    /**
     * Erro genérico de credenciais, igual para e-mail desconhecido e senha incorreta, para não revelar contas.
     *
     * @return exceção HTTP 401
     */
    private ApiException credenciaisInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
    }
}
