package com.supermarket.repository;
import com.supermarket.domain.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @EntityGraph(attributePaths = "employee")
    Optional<Account> findByAccountName(String accountName);
}
