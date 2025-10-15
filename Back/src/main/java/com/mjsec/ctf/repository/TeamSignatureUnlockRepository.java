package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamSignatureUnlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface TeamSignatureUnlockRepository extends JpaRepository<TeamSignatureUnlockEntity, Long> {

    boolean existsByTeamIdAndChallengeId(Long teamId, Long challengeId);

    List<TeamSignatureUnlockEntity> findByChallengeId(Long challengeId);
    List<TeamSignatureUnlockEntity> findByTeamId(Long teamId);

    @Transactional
    long deleteByChallengeId(Long challengeId);

    @Transactional
    long deleteByTeamIdAndChallengeId(Long teamId, Long challengeId);
}
