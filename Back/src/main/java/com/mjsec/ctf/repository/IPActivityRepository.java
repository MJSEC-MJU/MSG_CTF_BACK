// src/main/java/com/mjsec/ctf/repository/IPActivityRepository.java
package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.IPActivityEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IPActivityRepository extends JpaRepository<IPActivityEntity, Long> {

    /**
     * 특정 IP의 특정 시간 범위 내 특정 활동 타입 카운트
     */
    @Query("""
        SELECT COUNT(a) FROM IPActivityEntity a
         WHERE a.ipAddress = :ipAddress
           AND a.activityType = :activityType
           AND a.activityTime >= :since
    """)
    long countByIpAndTypeAndTimeSince(String ipAddress,
                                      IPActivityEntity.ActivityType activityType,
                                      LocalDateTime since);

    /**
     * 특정 IP의 특정 시간 범위 내 전체 활동 카운트
     */
    @Query("""
        SELECT COUNT(a) FROM IPActivityEntity a
         WHERE a.ipAddress = :ipAddress
           AND a.activityTime >= :since
    """)
    long countByIpAndTimeSince(String ipAddress, LocalDateTime since);

    /**
     * 특정 시간 이전의 활동 기록 삭제 (정리용)
     */
    @Modifying
    @Query("DELETE FROM IPActivityEntity a WHERE a.activityTime < :before")
    int deleteOldActivities(LocalDateTime before);

    /**
     * 특정 IP의 최근 활동 조회
     */
    @Query("""
        SELECT a FROM IPActivityEntity a
         WHERE a.ipAddress = :ipAddress
           AND a.activityTime >= :since
         ORDER BY a.activityTime DESC
    """)
    List<IPActivityEntity> findRecentActivitiesByIp(String ipAddress, LocalDateTime since);

    /**
     * 의심스러운 활동 조회
     */
    @Query("""
        SELECT a FROM IPActivityEntity a
         WHERE a.isSuspicious = true
           AND a.activityTime >= :since
         ORDER BY a.activityTime DESC
    """)
    List<IPActivityEntity> findSuspiciousActivities(LocalDateTime since);

    /**
     * 최근 활동 전체 조회 (관리자용)
     */
    @Query("""
        SELECT a FROM IPActivityEntity a
         WHERE a.activityTime >= :since
         ORDER BY a.activityTime DESC
    """)
    List<IPActivityEntity> findRecentActivities(LocalDateTime since);

    /**
     * 특정 IP + 활동 타입으로 조회
     */
    @Query("""
        SELECT a FROM IPActivityEntity a
         WHERE a.ipAddress = :ipAddress
           AND a.activityType = :activityType
           AND a.activityTime >= :since
         ORDER BY a.activityTime DESC
    """)
    List<IPActivityEntity> findByIpAndType(String ipAddress,
                                           IPActivityEntity.ActivityType activityType,
                                           LocalDateTime since);

    /**
     * 관리자 검색용 동적 필터 (ipAddress / activityType / isSuspicious 각각 독립 적용)
     * - ipAddress, activityType, isSuspicious 에 null을 넘기면 해당 조건은 건너뜀
     * - Pageable 로 limit/offset 적용, 정렬은 pageable의 sort 또는 기본 정렬 사용
     *
     * 사용 예)
     *   repo.searchActivities(
     *     since, null, ActivityType.LOGIN_FAILED, true, PageRequest.of(0, 100, Sort.by(DESC, "activityTime"))
     *   );
     */
    @Query("""
        SELECT a FROM IPActivityEntity a
         WHERE a.activityTime >= :since
           AND (:ipAddress IS NULL OR a.ipAddress = :ipAddress)
           AND (:activityType IS NULL OR a.activityType = :activityType)
           AND (:isSuspicious IS NULL OR a.isSuspicious = :isSuspicious)
         ORDER BY a.activityTime DESC
    """)
    List<IPActivityEntity> searchActivities(LocalDateTime since,
                                            String ipAddress,
                                            IPActivityEntity.ActivityType activityType,
                                            Boolean isSuspicious,
                                            Pageable pageable);

    /**
     * 의심스러운 IP 집계 (IP별 의심 활동 횟수)
     * 반환값: [ipAddress, count, maxActivityTime, maxLoginId, lastActivityType, lastDetails]
     *
     * NOTE: 아래 JPQL 서브쿼리의 LIMIT/OFFSET은 JPA 표준이 아니며, 필요 시 nativeQuery로 전환하세요.
     * 현재 프로젝트에서 사용 중이라면 그대로 유지합니다.
     */
    @Query("""
        SELECT a.ipAddress,
               COUNT(a),
               MAX(a.activityTime),
               MAX(a.loginId),
               (SELECT a2.activityType FROM IPActivityEntity a2
                 WHERE a2.ipAddress = a.ipAddress AND a2.isSuspicious = true
                 ORDER BY a2.activityTime DESC LIMIT 1),
               (SELECT a3.details FROM IPActivityEntity a3
                 WHERE a3.ipAddress = a.ipAddress AND a3.isSuspicious = true
                 ORDER BY a3.activityTime DESC LIMIT 1)
          FROM IPActivityEntity a
         WHERE a.isSuspicious = true
           AND a.activityTime >= :since
         GROUP BY a.ipAddress
         ORDER BY COUNT(a) DESC
    """)
    List<Object[]> findSuspiciousIPsSummary(LocalDateTime since);
}
