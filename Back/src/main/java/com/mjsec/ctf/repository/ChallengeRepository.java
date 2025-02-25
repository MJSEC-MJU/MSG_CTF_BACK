package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.ChallengeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity,Long> {

    //모든 문제 조회 (id 순)
    @Query("SELECT c FROM ChallengeEntity c ORDER BY c.ChallengeId ASC")
    Page<ChallengeEntity> findAllByOrderByChallengeIdAsc(Pageable pageable);

    Optional<ChallengeEntity> findById(Long challengeId);

}
