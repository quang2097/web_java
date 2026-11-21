package com.supermarket.repository;
import com.supermarket.domain.InvoiceLine;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    @EntityGraph(attributePaths = {"sku", "sku.product"})
    List<InvoiceLine> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    /** Dòng hoá đơn đã thanh toán trong khoảng thời gian (dùng cho báo cáo và dự báo). */
    @EntityGraph(attributePaths = {"sku", "sku.product", "invoice"})
    @Query("""
        select l from InvoiceLine l where l.invoice.status = com.supermarket.domain.Invoice.Status.PAID
        and l.invoice.paidAt >= :from and l.invoice.paidAt < :to""")
    List<InvoiceLine> findPaidBetween(Instant from, Instant to);
}