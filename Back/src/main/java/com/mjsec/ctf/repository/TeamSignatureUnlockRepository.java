package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamSignatureUnlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSignatureUnlockRepository extends JpaRepository<TeamSignatureUnlockEntity, Long> {
    boolean existsByTeamIdAndChallengeId(Long teamId, Long challengeId);
    void deleteByChallengeId(Long challengeId);
}
