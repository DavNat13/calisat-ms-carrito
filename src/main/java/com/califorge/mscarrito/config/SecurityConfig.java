package com.califorge.mscarrito.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cadena de seguridad: filter chain, CORS y reglas de autorizacion.
 *
 * <p>La validacion de los JWT (multi-emisor Azure + Cognito) vive en
 * {@link JwtDecoderConfig} y la conversion de roles en
 * {@link CalisatJwtAuthenticationConverter}; aqui solo se inyectan ambos beans.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ALLOWED_ORIGIN =
            "https://ezeh839whh.execute-api.us-east-1.amazonaws.com";

    /** Credencial MS->MS (misma clave que publican los demas microservicios). */
    @Value("${calisat.servicio.token:}")
    private String tokenDeServicio;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Carrito del usuario: JWT con rol CLIENTE. Las llamadas
                // servidor a servidor (orden vacia/lee, notificaciones lee
                // carritos abandonados) llegan con X-Service-Token -> SERVICIO.
                .requestMatchers("/api/v1/carrito", "/api/v1/carrito/**")
                        .hasAnyRole("CLIENTE", "SERVICIO")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .addFilterBefore(new ServiceTokenFilter(tokenDeServicio),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Envuelve el conversor de roles (que produce autoridades) en el converter que
     * Spring Security espera para montar la autenticacion a partir del JWT.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            CalisatJwtAuthenticationConverter roleConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roleConverter);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(ALLOWED_ORIGIN));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
