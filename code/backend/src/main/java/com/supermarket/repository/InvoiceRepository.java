package com.supermarket.repository;
import com.supermarket.domain.Invoice;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @Query("select i from Invoice i where i.status = com.supermarket.domain.Invoice.Status.PAID and i.paidAt >= :from and i.paidAt < :to")
    List<Invoice> findPaidBetween(Instant from, Instant to);

    @EntityGraph(attributePaths = {"employee", "customer"})
    @Query("select i from Invoice i order by i.createdAt desc")
    List<Invoice> findRecent(Pageable page);
}