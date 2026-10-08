package com.califorge.mscarrito.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica las llamadas MS->MS: si la peticion trae X-Service-Token valida,
 * publica un Authentication con ROLE_SERVICIO en el SecurityContext y los
 * matchers de SecurityConfig que aceptan SERVICIO dejan pasar la peticion
 * (reservar stock, vaciar carrito, publicar eventos...). Sin ese token la
 * peticion sigue exigiendo JWT como hasta ahora.
 *
 * No es un bean: lo instancia SecurityConfig con addFilterBefore para que no
 * se registre ademas como filtro de servlet.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";
    public static final String ROL = "ROLE_SERVICIO";

    private final String tokenEsperado;

    public ServiceTokenFilter(String tokenEsperado) {
        this.tokenEsperado = tokenEsperado;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        if (tokenEsperado != null && !tokenEsperado.isBlank()
                && tokenEsperado.equals(recibido)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    "servicio", null, List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        }
        filterChain.doFilter(request, response);
    }
}
