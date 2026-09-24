package com.califorge.mscarrito.client;

import com.califorge.mscarrito.exception.SkuNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CatalogoClient cliente() {
        return new CatalogoClient(restTemplate, "http://localhost:8082");
    }

    @Test
    void buscarPorSku_devuelveElProductoCuandoElCatalogoResponde() {
        ProductoDto producto = new ProductoDto("ANILLAS-001", "Anillas", new BigDecimal("19.99"), true);
        when(restTemplate.getForObject(
                "http://localhost:8082/api/v1/catalogo/{sku}", ProductoDto.class, "ANILLAS-001"))
                .thenReturn(producto);

        Optional<ProductoDto> resultado = cliente().buscarPorSku("ANILLAS-001");

        assertTrue(resultado.isPresent());
        assertEquals("ANILLAS-001", resultado.get().sku());
        assertEquals(0, new BigDecimal("19.99").compareTo(resultado.get().precio()));
        verify(restTemplate).getForObject(
                eq("http://localhost:8082/api/v1/catalogo/{sku}"), eq(ProductoDto.class), eq("ANILLAS-001"));
    }

    @Test
    void buscarPorSku_lanza404CuandoElCatalogoNoTieneElSku() {
        when(restTemplate.getForObject(
                "http://localhost:8082/api/v1/catalogo/{sku}", ProductoDto.class, "NO-EXISTE"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        assertThrows(SkuNoEncontradoException.class, () -> cliente().buscarPorSku("NO-EXISTE"));
    }

    @Test
    void buscarPorSku_degradaAVacioCuandoElCatalogoEstaCaido() {
        when(restTemplate.getForObject(
                "http://localhost:8082/api/v1/catalogo/{sku}", ProductoDto.class, "SKU-1"))
                .thenThrow(new ResourceAccessException("Connection refused"));

        Optional<ProductoDto> resultado = cliente().buscarPorSku("SKU-1");

        assertTrue(resultado.isEmpty());
    }
}
