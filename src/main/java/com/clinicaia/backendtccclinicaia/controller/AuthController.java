package com.clinicaia.backendtccclinicaia.controller;

import com.clinicaia.backendtccclinicaia.dto.auth.LoginRequest;
import com.clinicaia.backendtccclinicaia.dto.auth.TokenResponse;
import com.clinicaia.backendtccclinicaia.dto.auth.DefinirSenhaRequest;
import com.clinicaia.backendtccclinicaia.dto.auth.ConviteStatusResponse;
import com.clinicaia.backendtccclinicaia.dto.auth.RedefinirSenhaRequest;
import com.clinicaia.backendtccclinicaia.dto.auth.SolicitarRecuperacaoSenhaRequest;
import com.clinicaia.backendtccclinicaia.dto.auth.SolicitacaoRecuperacaoResponse;
import com.clinicaia.backendtccclinicaia.service.AuthService;
import com.clinicaia.backendtccclinicaia.service.ConviteMedicoService;
import com.clinicaia.backendtccclinicaia.service.RecuperacaoSenhaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de acesso: autenticação, ativação de conta por convite e recuperação de senha.
 *
 * <p>Todos os demais endpoints da API exigem o papel de médico (ver {@code SecurityConfig}).</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final ConviteMedicoService conviteService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;

    /**
     * Cria o controlador com os serviços de acesso.
     *
     * @param service autenticação por e-mail e senha
     * @param conviteService consulta e consumo de convites de ativação
     * @param recuperacaoSenhaService recuperação de senha por e-mail
     */
    public AuthController(
            AuthService service,
            ConviteMedicoService conviteService,
            RecuperacaoSenhaService recuperacaoSenhaService
    ) {
        this.service = service;
        this.conviteService = conviteService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
    }

    /**
     * Autentica o médico e emite o token JWT.
     *
     * @param request e-mail e senha informados
     * @return token de acesso, validade e dados básicos do médico
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(service.login(request));
    }

    /**
     * Valida um convite de ativação sem consumi-lo, para a tela de definição de senha.
     *
     * @param token código recebido por e-mail
     * @return dados do médico convidado
     */
    @GetMapping("/convite")
    public ConviteStatusResponse consultarConvite(@RequestParam String token) {
        return conviteService.consultar(token);
    }

    /**
     * Consome o convite e grava a senha escolhida pelo médico.
     *
     * @param request código do convite e nova senha
     * @return HTTP 204 sem corpo
     */
    @PostMapping("/definir-senha")
    public ResponseEntity<Void> definirSenha(@Valid @RequestBody DefinirSenhaRequest request) {
        conviteService.definirSenha(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Solicita a recuperação com resposta neutra, para não revelar quais e-mails têm conta.
     *
     * @param request e-mail da conta
     * @return mensagem genérica, igual exista ou não a conta
     */
    @PostMapping("/solicitar-recuperacao")
    public SolicitacaoRecuperacaoResponse solicitarRecuperacao(
            @Valid @RequestBody SolicitarRecuperacaoSenhaRequest request
    ) {
        return recuperacaoSenhaService.solicitar(request.email());
    }

    /**
     * Consome o token de uso único e grava a nova senha como hash BCrypt.
     *
     * @param request token recebido por e-mail e nova senha
     * @return HTTP 204 sem corpo
     */
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        recuperacaoSenhaService.redefinir(request);
        return ResponseEntity.noContent().build();
    }
}
