package com.califorge.mscarrito.config;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;


final class JwtClaims {

    private JwtClaims() {
    }

    /**
     * Devuelve el claim como lista de strings; una cadena suelta se devuelve como
     * lista de un elemento y el claim ausente o nulo como lista vacia.
     */
    static List<String> asStringList(Jwt jwt, String claim) {
        Object valor = jwt.getClaim(claim);

        if (valor == null) {
            return List.of();
        }
        if (valor instanceof List<?> lista) {
            return lista.stream()
                    .filter(elemento -> elemento != null)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of(String.valueOf(valor));
    }
}
