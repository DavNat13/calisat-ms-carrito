package com.califorge.mscarrito.service;

import com.califorge.mscarrito.dto.CarritoItemRequest;
import com.califorge.mscarrito.dto.CarritoResponse;
import com.califorge.mscarrito.exception.CantidadInvalidaException;
import com.califorge.mscarrito.exception.ItemCarritoNoEncontradoException;
import com.califorge.mscarrito.model.Carrito;
import com.califorge.mscarrito.model.CarritoItem;
import com.califorge.mscarrito.model.EstadoCarrito;
import com.califorge.mscarrito.repository.CarritoItemRepository;
import com.califorge.mscarrito.repository.CarritoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica de negocio del carrito de compras.
 */
@Service
@Transactional
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final CarritoItemRepository carritoItemRepository;

    public CarritoService(CarritoRepository carritoRepository,
                          CarritoItemRepository carritoItemRepository) {
        this.carritoRepository = carritoRepository;
        this.carritoItemRepository = carritoItemRepository;
    }

    /**
     * Devuelve el carrito del usuario; lo crea (ABIERTO, vacio) si aun no existe.
     */
    public CarritoResponse obtenerPorUsuario(String azureSub) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        List<CarritoItem> items = carritoItemRepository.findByCarritoId(carrito.getId());
        return CarritoResponse.desde(carrito, items);
    }

    /**
     * Agrega un item por SKU. Si el SKU ya esta en el carrito suma la cantidad
     * (upsert); si no, crea el item.
     */
    public CarritoItem agregarItem(String azureSub, CarritoItemRequest request) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        CarritoItem item = carritoItemRepository
                .findByCarritoIdAndSku(carrito.getId(), request.sku())
                .map(existente -> {
                    existente.setCantidad(existente.getCantidad() + request.cantidad());
                    return existente;
                })
                .orElseGet(() -> new CarritoItem(carrito, request.sku(), request.cantidad()));
        CarritoItem guardado = carritoItemRepository.save(item);
        marcarActualizacion(carrito);
        return guardado;
    }

    /**
     * Reemplaza la cantidad de un item existente. 404 si el SKU no esta en el
     * carrito; 400 si la cantidad no es positiva (defensa en profundidad junto
     * a la validacion @Min(1) del DTO).
     */
    public CarritoItem actualizarItem(String azureSub, String sku, int cantidad) {
        if (cantidad <= 0) {
            throw new CantidadInvalidaException(cantidad);
        }
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        CarritoItem item = carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), sku)
                .orElseThrow(() -> new ItemCarritoNoEncontradoException(sku));
        item.setCantidad(cantidad);
        CarritoItem guardado = carritoItemRepository.save(item);
        marcarActualizacion(carrito);
        return guardado;
    }

    /**
     * Elimina un item del carrito. 404 si el SKU no esta en el carrito.
     */
    public void eliminarItem(String azureSub, String sku) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        CarritoItem item = carritoItemRepository.findByCarritoIdAndSku(carrito.getId(), sku)
                .orElseThrow(() -> new ItemCarritoNoEncontradoException(sku));
        carritoItemRepository.delete(item);
        marcarActualizacion(carrito);
    }

    /**
     * Vacia todos los items del carrito (idempotente; 204 aunque ya estuviera vacio).
     */
    public void vaciar(String azureSub) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        carritoItemRepository.deleteByCarritoId(carrito.getId());
        marcarActualizacion(carrito);
    }

    /**
     * Valida el carrito y lo devuelve con items y advertencias: vacio,
     * cantidad total de unidades y precios sin verificar.
     */
    public CarritoResponse validar(String azureSub) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        List<CarritoItem> items = carritoItemRepository.findByCarritoId(carrito.getId());
        List<String> advertencias = calcularAdvertencias(items);
        return CarritoResponse.desde(carrito, items, advertencias);
    }

    private Carrito obtenerOCrearCarrito(String azureSub) {
        return carritoRepository.findByUsuarioSub(azureSub)
                .orElseGet(() -> {
                    Carrito nuevo = new Carrito();
                    nuevo.setUsuarioSub(azureSub);
                    nuevo.setEstado(EstadoCarrito.ABIERTO);
                    return carritoRepository.save(nuevo);
                });
    }

    private void marcarActualizacion(Carrito carrito) {
        carrito.setFechaActualizacion(LocalDateTime.now());
        carritoRepository.save(carrito);
    }

    private List<String> calcularAdvertencias(List<CarritoItem> items) {
        List<String> advertencias = new ArrayList<>();
        if (items.isEmpty()) {
            advertencias.add("El carrito esta vacio");
            return advertencias;
        }

        int totalUnidades = items.stream()
                .mapToInt(item -> item.getCantidad() == null ? 0 : item.getCantidad())
                .sum();
        advertencias.add("Cantidad total de unidades: " + totalUnidades);

        if (totalUnidades > 50) {
            advertencias.add("El carrito supera las 50 unidades; revisa las cantidades antes de confirmar");
        }

        items.stream()
                .filter(item -> item.getPrecioUnitarioVisto() == null)
                .forEach(item -> advertencias.add("El item '" + item.getSku() + "' no tiene precio verificado"));

        return advertencias;
    }
}
