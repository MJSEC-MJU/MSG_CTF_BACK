package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TeamHistoryRepository extends JpaRepository<TeamHistoryEntity, Long> {
    // 필요 시 사용자별 조회 등의 메소드를 추가할 수 있음
    List<TeamHistoryEntity> findAllByOrderBySolvedTimeAsc();

    // 특정 문제의 모든 팀 제출 기록 조회 (관리자용)
    List<TeamHistoryEntity> findByChallengeId(Long challengeId);

    // 특정 팀의 특정 문제 제출 기록 조회
    List<TeamHistoryEntity> findByTeamNameAndChallengeId(String teamName, Long challengeId);
}