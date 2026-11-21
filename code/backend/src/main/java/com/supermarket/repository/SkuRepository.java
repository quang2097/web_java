package com.supermarket.repository;
import com.supermarket.domain.Sku;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkuRepository extends JpaRepository<Sku, Long> {
    @EntityGraph(attributePaths = {"product", "product.category"})
    Optional<Sku> findByCode(String code);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"product", "product.category"})
    List<Sku> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("""
        select s from Sku s where s.product.active = true and (:q = ''
            or lower(s.product.name) like lower(concat('%', :q, '%'))
            or lower(s.code) like lower(concat('%', :q, '%')))
        order by s.product.name, s.variant""")
    List<Sku> search(String q);

    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("select s from Sku s where s.product.active = true order by s.product.name, s.variant")
    List<Sku> findAllActive();
}