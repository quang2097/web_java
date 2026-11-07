package com.supermarket.repository;
import com.supermarket.domain.Warehouse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findFirstByOrderByIdAsc();
    boolean existsByCode(String code);
}