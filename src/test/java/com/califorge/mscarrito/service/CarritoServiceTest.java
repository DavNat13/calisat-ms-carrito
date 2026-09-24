package com.califorge.mscarrito.service;

import com.califorge.mscarrito.client.CatalogoClient;
import com.califorge.mscarrito.client.InventarioClient;
import com.califorge.mscarrito.client.ProductoDto;
import com.califorge.mscarrito.client.StockDto;
import com.califorge.mscarrito.dto.CarritoItemRequest;
import com.califorge.mscarrito.dto.CarritoResponse;
import com.califorge.mscarrito.exception.CantidadInvalidaException;
import com.califorge.mscarrito.exception.ItemCarritoNoEncontradoException;
import com.califorge.mscarrito.exception.SkuNoEncontradoException;
import com.califorge.mscarrito.exception.StockInsuficienteException;
import com.califorge.mscarrito.model.Carrito;
import com.califorge.mscarrito.model.CarritoItem;
import com.califorge.mscarrito.model.EstadoCarrito;
import com.califorge.mscarrito.repository.CarritoItemRepository;
import com.califorge.mscarrito.repository.CarritoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    private static final String SUB = "sub-azure-1";

    @Mock
    private CarritoRepository carritoRepository;

    @Mock
    private CarritoItemRepository carritoItemRepository;

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private InventarioClient inventarioClient;

    @InjectMocks
    private CarritoService carritoService;

    @Test
    void obtenerPorUsuario_creaCarritoSiNoExiste() {
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.empty());
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(invocation -> {
            Carrito guardado = invocation.getArgument(0);
            guardado.setId(UUID.randomUUID());
            guardado.setFechaCreacion(LocalDateTime.of(2026, 9, 23, 10, 0));
            guardado.setFechaActualizacion(LocalDateTime.of(2026, 9, 23, 10, 0));
            return guardado;
        });
        when(carritoItemRepository.findByCarritoId(any(UUID.class))).thenReturn(List.of());

        CarritoResponse response = carritoService.obtenerPorUsuario(SUB);

        assertEquals(SUB, response.usuarioSub());
        assertEquals(EstadoCarrito.ABIERTO, response.estado());
        assertTrue(response.items().isEmpty());
        assertTrue(response.advertencias().isEmpty());
        verify(carritoRepository).save(any(Carrito.class));
    }

    @Test
    void obtenerPorUsuario_devuelveElCarritoExistenteSinCrearOtro() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoId(carrito.getId()))
                .thenReturn(List.of(item(carrito, "SKU-1", 2)));

        CarritoResponse response = carritoService.obtenerPorUsuario(SUB);

        assertEquals(carrito.getId(), response.id());
        assertEquals(1, response.items().size());
        assertEquals("SKU-1", response.items().get(0).sku());
        verify(carritoRepository, never()).save(any(Carrito.class));
    }

    @Test
    void agregarItem_sumaLaCantidadCuandoElSkuYaExiste() {
        Carrito carrito = carritoExistente();
        CarritoItem existente = item(carrito, "SKU-1", 2);
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-1"))
                .thenReturn(Optional.of(existente));
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarritoItem resultado = carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-1", 3));

        assertEquals(5, resultado.getCantidad());
        assertEquals("SKU-1", resultado.getSku());
        assertNull(resultado.getPrecioUnitarioVisto());
    }

    @Test
    void agregarItem_creaElItemCuandoElSkuNoExiste() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-2"))
                .thenReturn(Optional.empty());
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarritoItem resultado = carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-2", 4));

        ArgumentCaptor<CarritoItem> captor = ArgumentCaptor.forClass(CarritoItem.class);
        verify(carritoItemRepository).save(captor.capture());
        CarritoItem guardado = captor.getValue();
        assertEquals("SKU-2", guardado.getSku());
        assertEquals(4, guardado.getCantidad());
        assertSame(carrito, guardado.getCarrito());
        assertEquals(4, resultado.getCantidad());
    }

    @Test
    void actualizarItem_reemplazaLaCantidad() {
        Carrito carrito = carritoExistente();
        CarritoItem existente = item(carrito, "SKU-1", 2);
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-1"))
                .thenReturn(Optional.of(existente));
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CarritoItem resultado = carritoService.actualizarItem(SUB, "SKU-1", 7);

        assertEquals(7, resultado.getCantidad());
    }

    @Test
    void actualizarItem_lanza404CuandoElSkuNoEstaEnElCarrito() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-404"))
                .thenReturn(Optional.empty());

        assertThrows(ItemCarritoNoEncontradoException.class,
                () -> carritoService.actualizarItem(SUB, "SKU-404", 2));
    }

    @Test
    void actualizarItem_lanza400CuandoLaCantidadNoEsPositiva() {
        assertThrows(CantidadInvalidaException.class,
                () -> carritoService.actualizarItem(SUB, "SKU-1", 0));
        verifyNoInteractions(carritoRepository, carritoItemRepository);
    }

    @Test
    void eliminarItem_lanza404CuandoElSkuNoEstaEnElCarrito() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-404"))
                .thenReturn(Optional.empty());

        assertThrows(ItemCarritoNoEncontradoException.class,
                () -> carritoService.eliminarItem(SUB, "SKU-404"));
    }

    @Test
    void eliminarItem_borraElItemExistente() {
        Carrito carrito = carritoExistente();
        CarritoItem existente = item(carrito, "SKU-1", 2);
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-1"))
                .thenReturn(Optional.of(existente));

        carritoService.eliminarItem(SUB, "SKU-1");

        verify(carritoItemRepository).delete(existente);
    }

    @Test
    void vaciar_borraTodosLosItemsDelCarrito() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));

        carritoService.vaciar(SUB);

        verify(carritoItemRepository).deleteByCarritoId(carrito.getId());
    }

    @Test
    void validar_devuelveAdvertenciaDeCarritoVacio() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoId(carrito.getId())).thenReturn(List.of());

        CarritoResponse response = carritoService.validar(SUB);

        assertTrue(response.advertencias().stream().anyMatch(a -> a.contains("vacio")));
    }

    @Test
    void validar_incluyeCantidadTotalYPreciosSinVerificar() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoId(carrito.getId()))
                .thenReturn(List.of(item(carrito, "SKU-1", 3), item(carrito, "SKU-2", 4)));

        CarritoResponse response = carritoService.validar(SUB);

        assertEquals(2, response.items().size());
        assertTrue(response.advertencias().stream().anyMatch(a -> a.contains("Cantidad total de unidades: 7")));
        assertTrue(response.advertencias().stream().anyMatch(a -> a.contains("SKU-1") && a.contains("precio")));
    }

    @Test
    void agregarItem_snapshotDePrecioCuandoCatalogoResponde() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-2"))
                .thenReturn(Optional.empty());
        when(catalogoClient.buscarPorSku("SKU-2"))
                .thenReturn(Optional.of(new ProductoDto("SKU-2", "Cuerda", new BigDecimal("5.50"), true)));
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CarritoItem resultado = carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-2", 1));

        assertEquals(0, new BigDecimal("5.50").compareTo(resultado.getPrecioUnitarioVisto()));
    }

    @Test
    void agregarItem_lanza404CuandoElSkuNoExisteEnCatalogo() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-404"))
                .thenReturn(Optional.empty());
        when(catalogoClient.buscarPorSku("SKU-404")).thenThrow(new SkuNoEncontradoException("SKU-404"));

        assertThrows(SkuNoEncontradoException.class,
                () -> carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-404", 1)));
        verify(carritoItemRepository, never()).save(any(CarritoItem.class));
    }

    @Test
    void agregarItem_lanza409CuandoElStockNoAlcanza() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-1"))
                .thenReturn(Optional.empty());
        when(inventarioClient.consultarStock("SKU-1"))
                .thenReturn(Optional.of(new StockDto("SKU-1", 2, 0)));

        StockInsuficienteException ex = assertThrows(StockInsuficienteException.class,
                () -> carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-1", 5)));
        assertTrue(ex.getMessage().contains("SKU-1"));
        verify(carritoItemRepository, never()).save(any(CarritoItem.class));
    }

    @Test
    void agregarItem_degradaCuandoCatalogoEVacioInventarioNoResponden() {
        Carrito carrito = carritoExistente();
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), "SKU-9"))
                .thenReturn(Optional.empty());
        when(catalogoClient.buscarPorSku("SKU-9")).thenReturn(Optional.empty());
        when(inventarioClient.consultarStock("SKU-9")).thenReturn(Optional.empty());
        when(carritoItemRepository.save(any(CarritoItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CarritoItem resultado = carritoService.agregarItem(SUB, new CarritoItemRequest("SKU-9", 2));

        assertEquals(2, resultado.getCantidad());
        assertNull(resultado.getPrecioUnitarioVisto());
        verify(carritoItemRepository).save(any(CarritoItem.class));
    }

    @Test
    void validar_revalidaPreciosYStockContraServiciosExternos() {
        Carrito carrito = carritoExistente();
        CarritoItem item = item(carrito, "SKU-1", 3);
        item.setPrecioUnitarioVisto(new BigDecimal("10.00"));
        when(carritoRepository.findByUsuarioSub(SUB)).thenReturn(Optional.of(carrito));
        when(carritoItemRepository.findByCarritoId(carrito.getId())).thenReturn(List.of(item));
        when(catalogoClient.buscarPorSku("SKU-1"))
                .thenReturn(Optional.of(new ProductoDto("SKU-1", "Anillas", new BigDecimal("12.50"), true)));
        when(inventarioClient.consultarStock("SKU-1"))
                .thenReturn(Optional.of(new StockDto("SKU-1", 1, 0)));

        CarritoResponse response = carritoService.validar(SUB);

        assertTrue(response.advertencias().stream().anyMatch(a -> a.contains("El precio de 'SKU-1' cambio")));
        assertTrue(response.advertencias().stream().anyMatch(a -> a.contains("supera la disponibilidad")));
    }

    private Carrito carritoExistente() {
        Carrito carrito = new Carrito();
        carrito.setId(UUID.randomUUID());
        carrito.setUsuarioSub(SUB);
        carrito.setEstado(EstadoCarrito.ABIERTO);
        carrito.setFechaCreacion(LocalDateTime.of(2026, 9, 23, 10, 0));
        carrito.setFechaActualizacion(LocalDateTime.of(2026, 9, 23, 10, 0));
        return carrito;
    }

    private CarritoItem item(Carrito carrito, String sku, int cantidad) {
        CarritoItem item = new CarritoItem(carrito, sku, cantidad);
        item.setId(UUID.randomUUID());
        item.setFechaAgregado(LocalDateTime.of(2026, 9, 23, 10, 0));
        return item;
    }
}
