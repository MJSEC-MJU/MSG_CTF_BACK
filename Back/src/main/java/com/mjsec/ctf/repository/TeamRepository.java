package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, Long> {

    Optional<TeamEntity> findByTeamName(String teamName);

    boolean existsByTeamName(String teamName);

    List<TeamEntity> findAllByOrderByTotalPointDescLastSolvedTimeAsc();

    @Query(value = "SELECT * FROM team WHERE JSON_CONTAINS(member_user_ids, CAST(:userId AS JSON))", nativeQuery = true)
    Optional<TeamEntity> findByMemberUserId(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM team WHERE JSON_CONTAINS(solved_challenge_ids, CAST(:challengeId AS JSON))", nativeQuery = true)
    List<TeamEntity> findTeamsBySolvedChallengeId(@Param("challengeId") Long challengeId);
}
