package com.clinicaia.backendtccclinicaia.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Configuração de segurança HTTP da API.
 *
 * <p>A API não mantém sessão (stateless) e autentica por JWT. Rotas públicas: login, consulta de convite,
 * definição de senha e recuperação de senha. Qualquer outra rota exige o papel {@code MEDICO}. O CORS é
 * restrito às origens listadas em {@code app.cors.allowed-origins}.</p>
 */
@Configuration
public class SecurityConfig {

    /**
     * Define a cadeia de filtros, as rotas públicas e as respostas padronizadas para 401 e 403.
     *
     * @param http construtor da configuração de segurança
     * @param jwtFilter filtro que autentica a requisição pelo token
     * @param objectMapper serializador das respostas de erro
     * @return cadeia de filtros configurada
     * @throws Exception se a configuração for inválida
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/convite").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/definir-senha").permitAll()
                        .requestMatchers(
                                HttpMethod.POST, "/api/auth/solicitar-recuperacao", "/api/auth/redefinir-senha"
                        ).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().hasRole("MEDICO"))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                                response, objectMapper, 401, "Autenticação necessária", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                                response, objectMapper, 403, "Acesso negado", request.getRequestURI())))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Codificador de senhas. As senhas são guardadas somente como hash BCrypt.
     *
     * @return codificador BCrypt
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Libera apenas as origens configuradas, com os métodos e cabeçalhos usados pelo frontend.
     *
     * @param allowedOrigins origens separadas por vírgula
     * @return fonte de configuração CORS aplicada a todas as rotas
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Escreve a resposta JSON de erro de segurança no mesmo formato dos demais erros da API.
     *
     * @param response resposta HTTP
     * @param objectMapper serializador JSON
     * @param status código HTTP (401 ou 403)
     * @param message mensagem para o cliente
     * @param path rota requisitada
     * @throws IOException se a escrita na resposta falhar
     */
    private static void writeSecurityError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String message,
            String path
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "mensagem", message,
                "caminho", path
        ));
    }
}
