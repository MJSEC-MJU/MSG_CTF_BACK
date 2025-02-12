package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.BlacklistedTokenEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.Optional;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedTokenEntity,Long> {

    boolean existsByToken(String token);

    @Transactional
    @Modifying
    @Query("DELETE FROM BlacklistedTokenEntity b WHERE b.expiration < :currentTime")
    int deleteExpiredTokens(Date currentTime);
}
