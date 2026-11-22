package com.clinicaia.backendtccclinicaia.service;

import com.clinicaia.backendtccclinicaia.exception.ApiException;
import com.clinicaia.backendtccclinicaia.model.Medico;
import com.clinicaia.backendtccclinicaia.model.Usuario;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.time.Duration;

/**
 * Envia por e-mail os links de convite e de recuperação de senha.
 *
 * <p>Nenhuma senha, temporária ou não, e nenhum dado clínico trafega nas mensagens: só um link pessoal e de uso
 * único. Com {@code app.mail.enabled=false} nada é enviado, e o fluxo depende da exposição do link em
 * desenvolvimento.</p>
 */
@Service
public class EmailConviteService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String remetente;

    /**
     * Cria o serviço com o remetente SMTP.
     *
     * @param mailSender cliente SMTP
     * @param enabled {@code true} para realmente enviar as mensagens
     * @param remetente endereço do remetente; vazio usa um endereço padrão
     */
    public EmailConviteService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.username:no-reply@sistema-clinico.local}") String remetente
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.remetente = remetente == null || remetente.isBlank() ? "no-reply@sistema-clinico.local" : remetente;
    }

    /**
     * Informa se o envio de e-mails está ligado.
     *
     * @return {@code true} se as mensagens são realmente enviadas
     */
    public boolean estaHabilitado() {
        return enabled;
    }

    /**
     * Envia o convite de ativação, com o link para o médico definir a própria senha.
     *
     * @param medico médico convidado
     * @param urlConvite link pessoal e de uso único
     * @throws ApiException HTTP 503 se o envio falhar
     */
    public void enviar(Medico medico, String urlConvite) {
        if (!enabled)
            return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(medico.getEmail());
            helper.setSubject("Defina sua senha — Sistema Clínico");
            helper.setText("""
                    <div style="font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#17353b">
                      <h1 style="color:#0f6c72">Bem-vindo ao Sistema Clínico</h1>
                      <p>Olá, <strong>%s</strong>.</p>
                      <p>Seu acesso à clínica <strong>%s</strong> foi criado. Para definir sua senha com segurança, use o botão abaixo.</p>
                      <p style="margin:28px 0"><a href="%s" style="background:#123f4a;color:#fff;padding:13px 20px;border-radius:9px;text-decoration:none">Definir minha senha</a></p>
                      <p>O link é pessoal, de uso único e expira em 24 horas. Se você não reconhece este convite, ignore esta mensagem.</p>
                    </div>
                    """.formatted(escape(medico.getNomeCompleto()), escape(medico.getClinicaAtuacao()), urlConvite), true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar o convite por e-mail; verifique a configuração SMTP"
            );
        }
    }

    /**
     * Envia o link temporário de redefinição de senha, sem incluir senha ou dado clínico.
     *
     * @param usuario conta que pediu a recuperação
     * @param url link pessoal e de uso único
     * @param validade prazo informado na mensagem
     * @throws ApiException HTTP 503 se o envio falhar
     */
    public void enviarRecuperacao(Usuario usuario, String url, Duration validade) {
        if (!enabled)
            return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(usuario.getEmail());
            helper.setSubject("Redefinição de senha — Sistema Clínico");
            helper.setText("""
                    <div style="font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#17353b">
                      <h1 style="color:#0f6c72">Redefinição de senha</h1>
                      <p>Olá, <strong>%s</strong>.</p><p>Recebemos uma solicitação para redefinir sua senha.</p>
                      <p style="margin:28px 0"><a href="%s" style="background:#123f4a;color:#fff;padding:13px 20px;border-radius:9px;text-decoration:none">Criar nova senha</a></p>
                      <p>O link é pessoal, de uso único e expira em %d minutos. Se você não solicitou a alteração, ignore esta mensagem.</p>
                    </div>
                    """.formatted(escape(usuario.getNomeCompleto()), url, validade.toMinutes()), true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível enviar a recuperação por e-mail; verifique a configuração SMTP"
            );
        }
    }

    /**
     * Escapa os caracteres do HTML, pois nome e clínica são digitados por usuários e entram no corpo da mensagem.
     *
     * @param value texto a inserir no HTML
     * @return texto seguro; vazio se o valor for nulo
     */
    private String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
