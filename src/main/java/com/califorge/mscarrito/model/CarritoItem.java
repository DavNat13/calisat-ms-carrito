package com.califorge.mscarrito.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "carrito_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carrito_item_sku", columnNames = {"carrito_id", "sku"})
})
public class CarritoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrito_id", nullable = false)
    private Carrito carrito;

    @NotBlank(message = "sku es obligatorio")
    @Size(max = 64, message = "sku no puede superar 64 caracteres")
    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @NotNull(message = "cantidad es obligatoria")
    @Min(value = 1, message = "cantidad debe ser al menos 1")
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** Snapshot de precio visto al agregar el item; null hasta la fase de integracion con catalogo. */
    @Column(name = "precio_unitario_visto", precision = 10, scale = 2)
    private BigDecimal precioUnitarioVisto;

    @Column(name = "fecha_agregado", nullable = false)
    private LocalDateTime fechaAgregado;

    @PrePersist
    protected void onCreate() {
        fechaAgregado = LocalDateTime.now();
    }

    public CarritoItem() {}

    public CarritoItem(Carrito carrito, String sku, Integer cantidad) {
        this.carrito = carrito;
        this.sku = sku;
        this.cantidad = cantidad;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Carrito getCarrito() { return carrito; }
    public void setCarrito(Carrito carrito) { this.carrito = carrito; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitarioVisto() { return precioUnitarioVisto; }
    public void setPrecioUnitarioVisto(BigDecimal precioUnitarioVisto) { this.precioUnitarioVisto = precioUnitarioVisto; }

    public LocalDateTime getFechaAgregado() { return fechaAgregado; }
    public void setFechaAgregado(LocalDateTime fechaAgregado) { this.fechaAgregado = fechaAgregado; }
}
