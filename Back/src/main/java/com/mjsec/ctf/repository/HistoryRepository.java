package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.HistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface HistoryRepository extends JpaRepository<HistoryEntity, Long> {
    // 필요 시 사용자별 조회 등의 메소드를 추가할 수 있음
    List<HistoryEntity> findAllByOrderBySolvedTimeAsc();

    @Query("SELECT COUNT(DISTINCT h.userId) FROM HistoryEntity h WHERE h.challengeId = :challengeId")
    long countDistinctByChallengeId(Long challengeId);
}
