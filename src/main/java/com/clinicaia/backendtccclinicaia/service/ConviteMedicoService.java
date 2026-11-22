package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.dto.auth.ConviteStatusResponse;
import com.clinicaia.backendtccclinicaia.dto.auth.DefinirSenhaRequest;
import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.ConviteMedico;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.repository.ConviteMedicoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * Emite, valida e consome convites de ativação de conta, de uso único.
 *
 * <p>O médico é cadastrado sem senha utilizável; o convite enviado por e-mail é o único caminho para ele definir a
 * própria senha e acessar o sistema. No banco fica apenas o hash do código.</p>
 */
@Service
public class ConviteMedicoService {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final ConviteMedicoRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final EmailConviteService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration expiration;
    private final String frontendUrl;
    private final boolean exposeLink;

    /**
     * Cria o serviço com suas dependências e propriedades.
     *
     * @param repository persistência dos convites
     * @param passwordEncoder codificador BCrypt da senha escolhida
     * @param emailService envio do convite por e-mail
     * @param expiration validade do convite
     * @param frontendUrl endereço do frontend, base do link enviado
     * @param exposeLink {@code true} devolve o link na resposta; somente para desenvolvimento local
     */
    public ConviteMedicoService(
            ConviteMedicoRepository repository,
            PasswordEncoder passwordEncoder,
            EmailConviteService emailService,
            @Value("${app.invite.expiration:PT24H}") Duration expiration,
            @Value("${app.frontend.url:http://localhost:5500}") String frontendUrl,
            @Value("${app.invite.expose-link:false}") boolean exposeLink
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.expiration = expiration;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.exposeLink = exposeLink;
    }

    /**
     * Gera o convite de ativação de um médico recém-cadastrado e o envia por e-mail.
     *
     * <p>O código do convite é aleatório e só o seu hash SHA-256 é persistido. Nenhuma senha trafega por e-mail.</p>
     *
     * @param medico médico convidado
     * @param criador médico que fez o convite
     * @return resultado do envio; a URL só é devolvida quando a exposição do link estiver habilitada (desenvolvimento)
     * @throws ApiException HTTP 503 se não houver SMTP configurado nem exposição de link
     */
    @Transactional
    public ConviteEmitido emitir(Medico medico, Medico criador) {
        if (!emailService.estaHabilitado() && !exposeLink) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure o SMTP ou habilite EXPOSE_INVITE_LINK apenas no desenvolvimento local"
            );
        }
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime now = LocalDateTime.now(ZONE_ID);
        ConviteMedico convite = new ConviteMedico();
        convite.setMedico(medico);
        convite.setCriadoPor(criador);
        convite.setTokenHash(hash(token));
        convite.setCriadoEm(now);
        convite.setExpiraEm(now.plus(expiration));
        repository.save(convite);
        String url = frontendUrl + "/#/definir-senha?token=" + token;
        emailService.enviar(medico, url);
        return new ConviteEmitido(emailService.estaHabilitado(), convite.getExpiraEm(), exposeLink ? url : null);
    }

    /**
     * Confere se o convite é válido, sem consumi-lo.
     *
     * @param token código recebido por e-mail
     * @return dados do médico convidado
     * @throws ApiException HTTP 400 se o convite for inválido, expirado ou já utilizado
     */
    @Transactional(readOnly = true)
    public ConviteStatusResponse consultar(String token) {
        ConviteMedico convite = obterValido(token);
        Medico medico = convite.getMedico();
        return new ConviteStatusResponse(true, medico.getNomeCompleto(), medico.getEmail(), medico.getClinicaAtuacao());
    }

    /**
     * Consome o convite e grava a senha escolhida pelo médico, que passa a poder autenticar-se.
     *
     * @param request código do convite e nova senha
     * @throws ApiException HTTP 400 se o convite for inválido, expirado ou já utilizado
     */
    @Transactional
    public void definirSenha(DefinirSenhaRequest request) {
        ConviteMedico convite = obterValido(request.token());
        Medico medico = convite.getMedico();
        medico.setSenha(passwordEncoder.encode(request.novaSenha()));
        medico.setSenhaDefinida(true);
        convite.setUtilizadoEm(LocalDateTime.now(ZONE_ID));
        repository.save(convite);
    }

    /**
     * Localiza o convite pelo hash do código e confere validade e uso único.
     *
     * @param token código recebido por e-mail
     * @return convite pendente e dentro do prazo
     * @throws ApiException HTTP 400 se o convite for inválido, expirado ou já utilizado
     */
    private ConviteMedico obterValido(String token) {
        ConviteMedico convite = repository.findByTokenHash(hash(token == null ? "" : token.trim()))
                .orElseThrow(this::invalido);
        if (!convite.estaValidoEm(LocalDateTime.now(ZONE_ID)))
            throw invalido();
        return convite;
    }

    /**
     * Calcula o hash que é persistido no lugar do código, para que um vazamento do banco não permita ativar contas.
     *
     * @param value código enviado por e-mail
     * @return hash SHA-256 em hexadecimal
     */
    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    /**
     * Erro único para convite inexistente, expirado ou já utilizado, sem distinguir os casos.
     *
     * @return exceção HTTP 400
     */
    private ApiException invalido() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Convite inválido, expirado ou já utilizado");
    }

    /**
     * Resultado da emissão de um convite.
     *
     * @param enviadoPorEmail se o e-mail foi realmente enviado
     * @param expiraEm validade do convite
     * @param urlDesenvolvimento link do convite, preenchido somente quando a exposição está habilitada
     */
    public record ConviteEmitido(boolean enviadoPorEmail, LocalDateTime expiraEm, String urlDesenvolvimento) {}
}
