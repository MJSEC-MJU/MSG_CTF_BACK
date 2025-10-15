package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.SignatureCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SignatureCodeRepository extends JpaRepository<SignatureCodeEntity, Long> {

    // 🔹 @SQLRestriction이 적용된 "정상(미삭제)" 레코드만
    Optional<SignatureCodeEntity> findByChallengeIdAndCodeDigest(Long challengeId, String codeDigest);

    boolean existsByAssignedTeamIdAndChallengeId(Long teamId, Long challengeId);

    List<SignatureCodeEntity> findAllByChallengeId(Long challengeId);

    // 🔹 파생 deleteBy... 는 보통 하드 삭제로 나가지만, 명시적 네이티브도 아래에 제공
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    long deleteByChallengeId(Long challengeId);

    // ===================== 추가: 소프트삭제 포함 조회/복구/하드삭제 =====================

    // 🔸 소프트 삭제된 행까지 포함해서 "어떤 상태든" 한 건 조회
    @Query(value = """
        SELECT * 
        FROM signature_code 
        WHERE challenge_id = :challengeId 
          AND code_digest   = :codeDigest 
        LIMIT 1
        """, nativeQuery = true)
    Optional<SignatureCodeEntity> findAnyByChallengeIdAndCodeDigest(
            @Param("challengeId") Long challengeId,
            @Param("codeDigest")  String codeDigest
    );

    // 🔸 소프트삭제된 행을 복구(undelete) + 재배정/초기화 (네이티브 업데이트)
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE signature_code
           SET deleted_at       = NULL,
               assigned_team_id = :teamId,
               code_hash        = :codeHash,
               consumed         = 0,
               consumed_at      = NULL,
               updated_at       = NOW()
         WHERE id = :id
        """, nativeQuery = true)
    int undeleteAndReset(
            @Param("id")      Long id,
            @Param("teamId")  Long teamId,
            @Param("codeHash") String codeHash
    );

    // 🔸 챌린지 단위 하드 삭제(퍼지용)
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM signature_code WHERE challenge_id = :challengeId", nativeQuery = true)
    int hardDeleteByChallengeId(@Param("challengeId") Long challengeId);
}
