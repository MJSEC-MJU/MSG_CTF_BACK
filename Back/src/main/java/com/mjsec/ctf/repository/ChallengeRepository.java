package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.ChallengeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ChallengeRepository extends JpaRepository<ChallengeEntity,Long> {
    
    //모든 문제 조회
    Page<ChallengeEntity> findAllChallenges(Pageable pageable);
}
