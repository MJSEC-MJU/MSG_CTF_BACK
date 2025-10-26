package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.IPActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IPActivityRepository extends JpaRepository<IPActivityEntity, Long> {

    /**
     * 특정 IP의 특정 시간 범위 내 특정 활동 타입 카운트
     */
    @Query("SELECT COUNT(a) FROM IPActivityEntity a WHERE a.ipAddress = :ipAddress " +
           "AND a.activityType = :activityType AND a.activityTime >= :since")
    long countByIpAndTypeAndTimeSince(String ipAddress,
                                      IPActivityEntity.ActivityType activityType,
                                      LocalDateTime since);

    /**
     * 특정 IP의 특정 시간 범위 내 전체 활동 카운트
     */
    @Query("SELECT COUNT(a) FROM IPActivityEntity a WHERE a.ipAddress = :ipAddress " +
           "AND a.activityTime >= :since")
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
    @Query("SELECT a FROM IPActivityEntity a WHERE a.ipAddress = :ipAddress " +
           "AND a.activityTime >= :since ORDER BY a.activityTime DESC")
    List<IPActivityEntity> findRecentActivitiesByIp(String ipAddress, LocalDateTime since);

    /**
     * 의심스러운 활동 조회
     */
    @Query("SELECT a FROM IPActivityEntity a WHERE a.isSuspicious = true " +
           "AND a.activityTime >= :since ORDER BY a.activityTime DESC")
    List<IPActivityEntity> findSuspiciousActivities(LocalDateTime since);

    /**
     * 최근 활동 전체 조회 (관리자용)
     */
    @Query("SELECT a FROM IPActivityEntity a WHERE a.activityTime >= :since " +
           "ORDER BY a.activityTime DESC")
    List<IPActivityEntity> findRecentActivities(LocalDateTime since);

    /**
     * 특정 IP + 활동 타입으로 조회
     */
    @Query("SELECT a FROM IPActivityEntity a WHERE a.ipAddress = :ipAddress " +
           "AND a.activityType = :activityType AND a.activityTime >= :since " +
           "ORDER BY a.activityTime DESC")
    List<IPActivityEntity> findByIpAndType(String ipAddress, IPActivityEntity.ActivityType activityType, LocalDateTime since);
}
