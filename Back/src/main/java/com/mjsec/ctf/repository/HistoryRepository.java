package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.HistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<HistoryEntity> findByUserId(String userId);

    @Query("SELECT COUNT(DISTINCT h.userId) FROM HistoryEntity h WHERE h.challengeId = :challengeId")
    long countDistinctByChallengeId(Long challengeId);

    @Query("SELECT COUNT(h) > 0 FROM HistoryEntity h WHERE h.userId = :userId AND h.challengeId = :challengeId")
    boolean existsByUserIdAndChallengeId(@Param("userId") String userId, @Param("challengeId") Long challengeId);

    @Query("SELECT DISTINCT h.userId FROM HistoryEntity h")
    List<String> findDistinctUserIds();

     // challengeId에 해당하는 HistoryEntity를 삭제하는 메서드
    @Transactional
    @Modifying
    @Query("DELETE FROM HistoryEntity h WHERE h.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);
}