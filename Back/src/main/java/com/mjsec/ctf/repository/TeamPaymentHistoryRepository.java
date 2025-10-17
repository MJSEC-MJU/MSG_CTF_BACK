package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamPaymentHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamPaymentHistoryRepository extends JpaRepository<TeamPaymentHistoryEntity, Long> {

    // 특정 팀의 결제 히스토리 조회
    List<TeamPaymentHistoryEntity> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    // 모든 결제 히스토리 조회 (관리자용)
    List<TeamPaymentHistoryEntity> findAllByOrderByCreatedAtDesc();
}
