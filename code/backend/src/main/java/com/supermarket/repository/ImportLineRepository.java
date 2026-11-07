package com.supermarket.repository;
import com.supermarket.domain.ImportLine;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ImportLineRepository extends JpaRepository<ImportLine, Long> {
    @EntityGraph(attributePaths = {"sku", "sku.product"})
    List<ImportLine> findByReceiptIdOrderByIdAsc(Long receiptId);
}