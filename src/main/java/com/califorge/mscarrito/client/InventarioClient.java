package com.califorge.mscarrito.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cliente HTTP de calisat-ms-inventario (fase B). Sin service discovery:
 * la base URL se fija con la variable CALISAT_INVENTARIO_URL (default
 * localhost:8083).
 *
 * <p>Resiliencia: si el inventario esta caido o el SKU no tiene registro de
 * stock, la validacion de disponibilidad se OMITE (degradacion elegante) y
 * el alta del item no se interrumpe; solo se rechaza cuando el inventario
 * responde con una cifra real de disponible.</p>
 */
@Component
public class InventarioClient {

    private static final Logger log = LoggerFactory.getLogger(InventarioClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InventarioClient(RestTemplate restTemplate,
                            @Value("${CALISAT_INVENTARIO_URL:http://localhost:8083}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Consulta el stock disponible de un SKU.
     *
     * @return el stock si el inventario respondio; vacio si esta caido o el
     *         SKU no tiene registro de stock
     */
    public Optional<StockDto> consultarStock(String sku) {
        try {
            StockDto stock = restTemplate.getForObject(
                    baseUrl + "/api/v1/stock/sku/{sku}", StockDto.class, sku);
            return Optional.ofNullable(stock);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Inventario sin registro de stock para el SKU '{}': se omite la validacion de disponibilidad", sku);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Inventario no disponible para el SKU '{}' ({}): se omite la validacion de disponibilidad",
                    sku, ex.getMessage());
            return Optional.empty();
        }
    }
}
