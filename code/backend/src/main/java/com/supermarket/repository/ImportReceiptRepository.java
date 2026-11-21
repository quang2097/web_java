package com.supermarket.repository;
import com.supermarket.domain.ImportReceipt;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface ImportReceiptRepository extends JpaRepository<ImportReceipt, Long> {
    @EntityGraph(attributePaths = {"supplier", "warehouse", "employee"})
    @Query("select r from ImportReceipt r order by r.createdAt desc")
    List<ImportReceipt> findRecent(Pageable page);
}