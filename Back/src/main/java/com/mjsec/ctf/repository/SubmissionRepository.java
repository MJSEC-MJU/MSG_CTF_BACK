package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.SubmissionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SubmissionEntity s WHERE s.loginId = :loginId AND s.challengeId = :challengeId")
    Optional<SubmissionEntity> findByLoginIdAndChallengeId(String loginId, Long challengeId);
}
