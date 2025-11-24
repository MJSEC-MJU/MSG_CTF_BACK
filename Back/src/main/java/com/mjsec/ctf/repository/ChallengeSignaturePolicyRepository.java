package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.ChallengeSignaturePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeSignaturePolicyRepository extends JpaRepository<ChallengeSignaturePolicy, Long> {
    Optional<ChallengeSignaturePolicy> findByChallengeId(Long challengeId);
    void deleteByChallengeId(Long challengeId);
}
