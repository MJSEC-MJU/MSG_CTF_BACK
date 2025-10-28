package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.IPWhitelistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPWhitelistRepository extends JpaRepository<IPWhitelistEntity, Long> {

    /**
     * IP 주소로 화이트리스트 엔티티 조회
     */
    Optional<IPWhitelistEntity> findByIpAddress(String ipAddress);

    /**
     * IP가 활성 화이트리스트에 있는지 확인
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM IPWhitelistEntity w " +
           "WHERE w.ipAddress = :ipAddress AND w.isActive = true")
    boolean isWhitelisted(String ipAddress);

    /**
     * 활성 화이트리스트 목록 조회
     */
    @Query("SELECT w FROM IPWhitelistEntity w WHERE w.isActive = true ORDER BY w.addedAt DESC")
    List<IPWhitelistEntity> findAllActive();

    /**
     * 전체 화이트리스트 목록 조회 (비활성 포함)
     */
    @Query("SELECT w FROM IPWhitelistEntity w ORDER BY w.addedAt DESC")
    List<IPWhitelistEntity> findAllWhitelist();

    /**
     * IP 주소가 이미 존재하는지 확인
     */
    boolean existsByIpAddress(String ipAddress);
}
