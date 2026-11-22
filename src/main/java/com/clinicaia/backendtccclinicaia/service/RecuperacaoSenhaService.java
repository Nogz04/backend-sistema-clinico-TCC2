package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.auth.*;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.*;
import com.clinicaia.backendtccclinicaia.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/**
 * Emite e consome tokens de recuperação de senha sem revelar a existência de contas.
 *
 * <p>O link enviado por e-mail carrega um código aleatório de uso único; no banco fica apenas o hash SHA-256
 * dele, com prazo de validade curto.</p>
 */
@Service
public class RecuperacaoSenhaService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    /** Mesma resposta para e-mail cadastrado ou não, para impedir a descoberta de contas. */
    private static final String MENSAGEM = "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha.";

    private final RecuperacaoSenhaRepository repository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final EmailConviteService emailService;
    private final SecureRandom random = new SecureRandom();
    private final Duration expiration;
    private final String frontendUrl;
    private final boolean exposeLink;

    /**
     * Cria o serviço com suas dependências e propriedades.
     *
     * @param repository persistência dos pedidos de recuperação
     * @param usuarios busca de contas pelo e-mail
     * @param encoder codificador BCrypt da nova senha
     * @param emailService envio do link por e-mail
     * @param expiration validade do link
     * @param frontendUrl endereço do frontend, base do link enviado
     * @param exposeLink {@code true} devolve o link na resposta; somente para desenvolvimento local
     */
    public RecuperacaoSenhaService(
            RecuperacaoSenhaRepository repository,
            UsuarioRepository usuarios,
            PasswordEncoder encoder,
            EmailConviteService emailService,
            @Value("${app.password-reset.expiration:PT30M}") Duration expiration,
            @Value("${app.frontend.url:http://localhost:5500}") String frontendUrl,
            @Value("${app.password-reset.expose-link:false}") boolean exposeLink
    ) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.emailService = emailService;
        this.expiration = expiration;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.exposeLink = exposeLink;
    }

    /**
     * Gera um link de redefinição de senha e o envia por e-mail.
     *
     * <p>A resposta é sempre a mesma, exista ou não conta com o e-mail informado, para não revelar quais contas
     * estão cadastradas. Contas que ainda não definiram a senha (convite pendente) não recebem link.</p>
     *
     * @param email e-mail informado pelo usuário
     * @return mensagem neutra e, somente em desenvolvimento, o link gerado
     */
    @Transactional
    public SolicitacaoRecuperacaoResponse solicitar(String email) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(email.trim()).filter(Usuario::isSenhaDefinida).orElse(null);
        if (usuario == null)
            return new SolicitacaoRecuperacaoResponse(MENSAGEM, null);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime agora = LocalDateTime.now(ZONE_ID);
        RecuperacaoSenha item = new RecuperacaoSenha();
        item.setUsuario(usuario);
        item.setTokenHash(hash(token));
        item.setCriadoEm(agora);
        item.setExpiraEm(agora.plus(expiration));
        repository.save(item);
        String url = frontendUrl + "/#/redefinir-senha?token=" + token;
        emailService.enviarRecuperacao(usuario, url, expiration);
        return new SolicitacaoRecuperacaoResponse(MENSAGEM, exposeLink ? url : null);
    }

    /**
     * Consome o link de uso único e grava a nova senha.
     *
     * @param request código recebido por e-mail e nova senha
     * @throws ApiException HTTP 400 se o link for inválido, expirado ou já utilizado
     */
    @Transactional
    public void redefinir(RedefinirSenhaRequest request) {
        RecuperacaoSenha item = repository.findByTokenHash(hash(request.token().trim())).orElseThrow(this::invalido);
        if (!item.estaValidoEm(LocalDateTime.now(ZONE_ID)))
            throw invalido();
        item.getUsuario().setSenha(encoder.encode(request.novaSenha()));
        item.setUtilizadoEm(LocalDateTime.now(ZONE_ID));
    }

    /**
     * Calcula o hash que é persistido no lugar do código, para que um vazamento do banco não permita redefinir senhas.
     *
     * @param value código enviado por e-mail
     * @return hash SHA-256 em hexadecimal
     */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Erro único para link inexistente, expirado ou já utilizado, sem distinguir os casos.
     *
     * @return exceção HTTP 400
     */
    private ApiException invalido() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Link de recuperação inválido, expirado ou já utilizado");
    }
}
