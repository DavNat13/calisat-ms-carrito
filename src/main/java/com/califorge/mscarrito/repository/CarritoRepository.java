package com.califorge.mscarrito.repository;

import com.califorge.mscarrito.model.Carrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, UUID> {

    Optional<Carrito> findByUsuarioSub(String usuarioSub);

    boolean existsByUsuarioSub(String usuarioSub);
}
