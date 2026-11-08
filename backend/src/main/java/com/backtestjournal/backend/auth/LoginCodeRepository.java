package com.backtestjournal.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginCode> findByLinkToken(String linkToken);

    @Transactional
    long deleteByExpiresAtBefore(Instant cutoff);
}
