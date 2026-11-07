package com.supermarket.repository;
import com.supermarket.domain.PasswordRequest;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
public interface PasswordRequestRepository extends JpaRepository<PasswordRequest, Long> {
    @EntityGraph(attributePaths = {"account", "account.employee", "handledBy"})
    @Query("select r from PasswordRequest r order by case when r.status = com.supermarket.domain.PasswordRequest.Status.PENDING then 0 else 1 end, r.createdAt desc")
    List<PasswordRequest> findAllForReview();

    @EntityGraph(attributePaths = {"account", "account.employee", "handledBy"})
    java.util.Optional<PasswordRequest> findWithDetailsById(Long id);

    boolean existsByAccountIdAndStatus(Long accountId, com.supermarket.domain.PasswordRequest.Status status);
}