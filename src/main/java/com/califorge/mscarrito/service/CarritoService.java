package com.califorge.mscarrito.service;

import com.califorge.mscarrito.client.CatalogoClient;
import com.califorge.mscarrito.client.InventarioClient;
import com.califorge.mscarrito.client.ProductoDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Logica de negocio del carrito de compras.
 *
 * Fase B: alta de items con validacion contra catalogo (SKU activo + snapshot
 * de precio) y disponibilidad en inventario. Las llamadas HTTP van por los
 * clientes del paquete client con degradacion elegante: si un MS dependiente
 * esta caido, la validacion se omite y el alta del item NO se interrumpe.
 */
@Service
@Transactional
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final CarritoItemRepository carritoItemRepository;
    private final CatalogoClient catalogoClient;
    private final InventarioClient inventarioClient;

    public CarritoService(CarritoRepository carritoRepository,
                          CarritoItemRepository carritoItemRepository,
                          CatalogoClient catalogoClient,
                          InventarioClient inventarioClient) {
        this.carritoRepository = carritoRepository;
        this.carritoItemRepository = carritoItemRepository;
        this.catalogoClient = catalogoClient;
        this.inventarioClient = inventarioClient;
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
     * (upsert); si no, crea el item. Antes de persistir valida el SKU contra
     * catalogo (404 si no existe/inactivo; snapshot de precio visto) y la
     * disponibilidad contra inventario (409 si no alcanza). Si esos MS estan
     * caidos, la validacion se omite y el alta continua.
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
        validarYSnapshotear(item);
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
     * cantidad total de unidades, precios sin verificar y (fase B)
     * revalidacion de precio/stock contra catalogo/inventario. La
     * revalidacion solo agrega advertencias; nunca interrumpe el flujo.
     */
    public CarritoResponse validar(String azureSub) {
        Carrito carrito = obtenerOCrearCarrito(azureSub);
        List<CarritoItem> items = carritoItemRepository.findByCarritoId(carrito.getId());
        List<String> advertencias = calcularAdvertencias(items);
        revalidarContraServiciosExternos(items, advertencias);
        return CarritoResponse.desde(carrito, items, advertencias);
    }

    /**
     * Validacion previa al alta del item:
     * <ul>
     *   <li>catalogo: 404 si el SKU no existe o esta inactivo; si responde,
     *       actualiza el snapshot de precio visto del item.</li>
     *   <li>inventario: 409 si la cantidad final supera el disponible. Si el
     *       inventario esta caido o no tiene registro, se omite.</li>
     * </ul>
     */
    private void validarYSnapshotear(CarritoItem item) {
        Optional<ProductoDto> producto = catalogoClient.buscarPorSku(item.getSku());
        if (producto.isPresent()) {
            if (!producto.get().activo()) {
                throw new SkuNoEncontradoException(item.getSku());
            }
            item.setPrecioUnitarioVisto(producto.get().precio());
        }

        inventarioClient.consultarStock(item.getSku()).ifPresent(stock -> {
            Integer disponible = stock.cantidadDisponible();
            if (disponible != null && item.getCantidad() != null && item.getCantidad() > disponible) {
                throw new StockInsuficienteException(item.getSku(), item.getCantidad(), disponible);
            }
        });
    }

    /**
     * Revalida precios y disponibilidad de los items del carrito (anti
     * "precio cambio") y agrega advertencias; degrada a vacio si un MS
     * dependiente no responde.
     */
    private void revalidarContraServiciosExternos(List<CarritoItem> items, List<String> advertencias) {
        for (CarritoItem item : items) {
            try {
                catalogoClient.buscarPorSku(item.getSku()).ifPresent(producto -> {
                    if (item.getPrecioUnitarioVisto() != null && producto.precio() != null
                            && item.getPrecioUnitarioVisto().compareTo(producto.precio()) != 0) {
                        advertencias.add("El precio de '" + item.getSku() + "' cambio: visto "
                                + item.getPrecioUnitarioVisto() + ", actual " + producto.precio());
                    }
                });
            } catch (SkuNoEncontradoException ex) {
                advertencias.add("El item '" + item.getSku() + "' ya no existe o esta inactivo en el catalogo");
            }

            inventarioClient.consultarStock(item.getSku()).ifPresent(stock -> {
                Integer disponible = stock.cantidadDisponible();
                if (disponible != null && item.getCantidad() != null && item.getCantidad() > disponible) {
                    advertencias.add("El item '" + item.getSku() + "' supera la disponibilidad ("
                            + disponible + " disponibles)");
                }
            });
        }
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
