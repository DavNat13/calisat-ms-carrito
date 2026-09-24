package com.califorge.mscarrito.repository;

import com.califorge.mscarrito.model.CarritoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarritoItemRepository extends JpaRepository<CarritoItem, UUID> {

    List<CarritoItem> findByCarritoId(UUID carritoId);

    Optional<CarritoItem> findByCarritoIdAndSku(UUID carritoId, String sku);

    void deleteByCarritoId(UUID carritoId);
}
