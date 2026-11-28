package com.supermarket.repository;
import com.supermarket.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    @Override
    @EntityGraph(attributePaths = "employee")
    Optional<Account> findById(Long id);

    @EntityGraph(attributePaths = "employee")
    Optional<Account> findByAccountName(String accountName);

    @EntityGraph(attributePaths = "employee")
    List<Account> findAllByOrderByIdAsc();
}
