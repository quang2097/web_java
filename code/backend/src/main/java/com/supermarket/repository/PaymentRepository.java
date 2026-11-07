package com.supermarket.repository;
import com.supermarket.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByInvoiceId(Long invoiceId);
}