package com.califorge.mscarrito.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioClientTest {

    @Mock
    private RestTemplate restTemplate;

    private InventarioClient cliente() {
        return new InventarioClient(restTemplate, "http://localhost:8083");
    }

    @Test
    void consultarStock_devuelveElStockDisponible() {
        when(restTemplate.getForObject(
                "http://localhost:8083/api/v1/stock/sku/{sku}", StockDto.class, "SKU-1"))
                .thenReturn(new StockDto("SKU-1", 7, 2));

        Optional<StockDto> resultado = cliente().consultarStock("SKU-1");

        assertTrue(resultado.isPresent());
        assertEquals(7, resultado.get().cantidadDisponible().intValue());
        assertEquals(2, resultado.get().cantidadReservada().intValue());
    }

    @Test
    void consultarStock_devuelveVacioSiElSkuNoTieneRegistro() {
        when(restTemplate.getForObject(
                "http://localhost:8083/api/v1/stock/sku/{sku}", StockDto.class, "SIN-STOCK"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        assertTrue(cliente().consultarStock("SIN-STOCK").isEmpty());
    }

    @Test
    void consultarStock_degradaAVacioSiInventarioEstaCaido() {
        when(restTemplate.getForObject(
                "http://localhost:8083/api/v1/stock/sku/{sku}", StockDto.class, "SKU-1"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertTrue(cliente().consultarStock("SKU-1").isEmpty());
    }
}
