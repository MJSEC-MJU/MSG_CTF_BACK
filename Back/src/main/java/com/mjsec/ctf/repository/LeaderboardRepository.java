package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.LeaderboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<LeaderboardEntity, Long> {

    List<LeaderboardEntity> findAllByOrderByTotalPointDescLastSolvedTimeAsc();

    Optional<LeaderboardEntity> findByUserId(String userId);
}