package com.clinicaia.backendtccclinicaia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/** Configuração SMTP explícita para manter comportamento consistente no Spring Boot 4. */
@Configuration
public class MailConfig {

    /**
     * Cria o remetente SMTP a partir das propriedades {@code spring.mail.*}, com tempos limite curtos para que um
     * servidor de e-mail lento não trave o cadastro de médicos.
     *
     * @param host servidor SMTP
     * @param port porta do servidor SMTP
     * @param username usuário de autenticação (vazio quando não há)
     * @param password senha de autenticação (vazia quando não há)
     * @param auth {@code true} para autenticar no servidor
     * @param startTls {@code true} para negociar conexão segura STARTTLS
     * @return remetente de e-mail configurado
     */
    @Bean
    JavaMailSender javaMailSender(
            @Value("${spring.mail.host:localhost}") String host,
            @Value("${spring.mail.port:1025}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean startTls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(auth));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        return sender;
    }
}
