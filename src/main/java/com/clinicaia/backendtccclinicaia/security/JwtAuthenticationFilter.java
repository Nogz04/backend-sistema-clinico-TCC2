package com.clinicaia.backendtccclinicaia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro executado a cada requisição que autentica o usuário a partir do cabeçalho {@code Authorization}.
 *
 * <p>Aceita apenas o esquema {@code Bearer}. Token ausente, inválido ou expirado deixa o contexto sem
 * autenticação, e a decisão de acesso fica a cargo da {@code SecurityConfig}.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UsuarioDetailsService usuarioDetailsService;

    /**
     * Cria o filtro com os serviços que validam o token e carregam o usuário.
     *
     * @param tokenService verifica a assinatura e a validade do JWT
     * @param usuarioDetailsService carrega o médico a partir do e-mail do token
     */
    public JwtAuthenticationFilter(TokenService tokenService, UsuarioDetailsService usuarioDetailsService) {
        this.tokenService = tokenService;
        this.usuarioDetailsService = usuarioDetailsService;
    }

    /**
     * Carrega o médico indicado pelo token válido e o registra no contexto de segurança da requisição.
     *
     * @param request requisição recebida
     * @param response resposta HTTP
     * @param filterChain restante da cadeia de filtros, sempre executado
     * @throws ServletException se um filtro seguinte falhar
     * @throws IOException se a leitura ou a escrita da requisição falhar
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = tokenService.validarEObterEmail(header.substring(7));
                UserDetails user = usuarioDetailsService.loadUserByUsername(email);
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
