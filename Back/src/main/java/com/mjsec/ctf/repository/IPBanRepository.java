package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.IPBanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IPBanRepository extends JpaRepository<IPBanEntity, Long> {

    Optional<IPBanEntity> findByIpAddress(String ipAddress);

    @Query("SELECT ip FROM IPBanEntity ip WHERE ip.ipAddress = :ipAddress AND ip.isActive = true")
    Optional<IPBanEntity> findActiveByIpAddress(String ipAddress);

    @Query("SELECT ip FROM IPBanEntity ip WHERE ip.isActive = true")
    List<IPBanEntity> findAllActiveBans();

    @Query("SELECT ip FROM IPBanEntity ip WHERE ip.banType = 'TEMPORARY' AND ip.expiresAt < :now AND ip.isActive = true")
    List<IPBanEntity> findExpiredBans(LocalDateTime now);

    boolean existsByIpAddressAndIsActiveTrue(String ipAddress);
}
