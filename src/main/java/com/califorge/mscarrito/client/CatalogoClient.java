package com.califorge.mscarrito.client;

import com.califorge.mscarrito.exception.SkuNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cliente HTTP de calisat-ms-catalogo (fase B). Sin service discovery:
 * la base URL es calisat.gateway.url (el API Gateway; cero IPs en el repo).
 *
 * <p>Resiliencia: un 404 del catalogo es un rechazo de negocio real
 * ({@link SkuNoEncontradoException} -> HTTP 404 en el carrito); cualquier
 * otra falla (caida de red, 5xx) degrada a Optional vacio y el alta del
 * item NO se interrumpe.</p>
 */
@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CatalogoClient(RestTemplate restTemplate,
                          @Value("${calisat.urls.catalogo:${calisat.gateway.url}}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Busca un producto por SKU en el catalogo.
     *
     * @return el producto si el catalogo respondio; vacio si el servicio esta caido
     * @throws SkuNoEncontradoException si el catalogo responde 404 para el SKU
     */
    public Optional<ProductoDto> buscarPorSku(String sku) {
        try {
            ProductoDto producto = restTemplate.getForObject(
                    baseUrl + "/api/v1/catalogo/{sku}", ProductoDto.class, sku);
            return Optional.ofNullable(producto);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new SkuNoEncontradoException(sku);
        } catch (RestClientException ex) {
            log.warn("Catalogo no disponible para el SKU '{}' ({}): se omite la validacion de precio/activo",
                    sku, ex.getMessage());
            return Optional.empty();
        }
    }
}
