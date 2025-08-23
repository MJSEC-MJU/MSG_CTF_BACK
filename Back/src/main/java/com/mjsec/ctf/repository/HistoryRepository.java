package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.HistoryEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface HistoryRepository extends JpaRepository<HistoryEntity, Long> {
    // 필요 시 사용자별 조회 등의 메소드를 추가할 수 있음
    List<HistoryEntity> findAllByOrderBySolvedTimeAsc();

    // userId → loginId로 변경
    List<HistoryEntity> findByLoginId(String loginId);

    @Query("SELECT COUNT(DISTINCT h.loginId) FROM HistoryEntity h WHERE h.challengeId = :challengeId AND h.userDeleted = false AND h.loginId IS NOT NULL")
    long countDistinctByChallengeId(Long challengeId);

    @Query("SELECT COUNT(h) > 0 FROM HistoryEntity h WHERE h.loginId = :loginId AND h.challengeId = :challengeId")
    boolean existsByLoginIdAndChallengeId(@Param("loginId") String loginId, @Param("challengeId") Long challengeId);

    // 메서드명 변경
    @Query("SELECT DISTINCT h.loginId FROM HistoryEntity h WHERE h.loginId IS NOT NULL AND h.userDeleted = false")
    List<String> findDistinctLoginIds();

    // challengeId에 해당하는 HistoryEntity를 삭제하는 메서드
    @Transactional
    @Modifying
    @Query("DELETE FROM HistoryEntity h WHERE h.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);

    // loginId에 해당하는 HistoryEntity를 삭제하는 메서드 (파라미터명과 쿼리 변경)
    @Transactional
    @Modifying
    @Query("DELETE FROM HistoryEntity h WHERE h.loginId = :loginId")
    void deleteByLoginId(@Param("loginId") String loginId);

    // 비관적 락을 적용하여 조회 (메서드명과 파라미터명, 쿼리 모두 변경)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HistoryEntity h WHERE h.loginId = :loginId AND h.challengeId = :challengeId")
    Optional<HistoryEntity> findWithLockByLoginIdAndChallengeId(@Param("loginId") String loginId, @Param("challengeId") Long challengeId);

    @Query("SELECT h FROM HistoryEntity h WHERE h.userDeleted = true")
    List<HistoryEntity> findByUserDeletedTrue();

    @Query("SELECT h FROM HistoryEntity h WHERE h.loginId = :loginId AND h.userDeleted = false")
    List<HistoryEntity> findByLoginIdAndUserDeletedFalse(@Param("loginId") String loginId);

    @Query("SELECT h FROM HistoryEntity h " +
            "JOIN ChallengeEntity c ON h.challengeId = c.challengeId " +
            "WHERE h.loginId = :loginId " +
            "AND h.userDeleted = false " +
            "AND c.deletedAt IS NULL")
    List<HistoryEntity> findByLoginIdAndUserDeletedFalseAndChallengeNotDeleted(@Param("loginId") String loginId);

}