package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.SubmissionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    Optional<SubmissionEntity> findByLoginIdAndChallengeId(String loginId, Long challengeId);

    @Modifying
    @Query("DELETE FROM SubmissionEntity s WHERE s.challengeId = :challengeId")
    void deleteByChallengeId(@Param("challengeId") Long challengeId);

    @Transactional
    @Modifying
    @Query("DELETE FROM SubmissionEntity s WHERE s.loginId = :loginId")
    void deleteByLoginId(@Param("loginId") String loginId);
}
