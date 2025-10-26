package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.IPActivityEntity;
import com.mjsec.ctf.domain.IPBanEntity;
import com.mjsec.ctf.repository.IPActivityRepository;
import com.mjsec.ctf.repository.IPBanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IPBanService {

    private final IPBanRepository ipBanRepository;
    private final IPActivityRepository ipActivityRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String BANNED_IP_KEY = "banned_ips";

    /**
     * IP 주소 차단
     */
    @Transactional
    public IPBanEntity banIP(String ipAddress, String reason, IPBanEntity.BanType banType,
                             Long durationMinutes, Long adminId, String adminLoginId) {

        // 이미 차단된 IP인지 확인
        Optional<IPBanEntity> existing = ipBanRepository.findByIpAddress(ipAddress);

        IPBanEntity banEntity;
        if (existing.isPresent()) {
            // 기존 차단 정보 업데이트
            banEntity = existing.get();
            banEntity.setReason(reason);
            banEntity.setBanType(banType);
            banEntity.setBannedAt(LocalDateTime.now());
            banEntity.setIsActive(true);
            banEntity.setBannedByAdminId(adminId);
            banEntity.setBannedByAdminLoginId(adminLoginId);
        } else {
            // 새로운 차단 생성
            banEntity = new IPBanEntity();
            banEntity.setIpAddress(ipAddress);
            banEntity.setReason(reason);
            banEntity.setBanType(banType);
            banEntity.setBannedAt(LocalDateTime.now());
            banEntity.setIsActive(true);
            banEntity.setBannedByAdminId(adminId);
            banEntity.setBannedByAdminLoginId(adminLoginId);
        }

        // 만료 시간 설정
        if (banType == IPBanEntity.BanType.TEMPORARY && durationMinutes != null) {
            banEntity.setExpiresAt(LocalDateTime.now().plusMinutes(durationMinutes));
        } else {
            banEntity.setExpiresAt(null);
        }

        IPBanEntity savedEntity = ipBanRepository.save(banEntity);

        // Redis에 캐시
        addToRedisCache(ipAddress);

        log.info("IP banned: {} | Type: {} | Reason: {} | By: {}",
                 ipAddress, banType, reason, adminLoginId);

        return savedEntity;
    }

    /**
     * IP 차단 해제
     */
    @Transactional
    public void unbanIP(String ipAddress) {
        Optional<IPBanEntity> banEntity = ipBanRepository.findByIpAddress(ipAddress);

        if (banEntity.isPresent()) {
            IPBanEntity entity = banEntity.get();
            entity.setIsActive(false);
            ipBanRepository.save(entity);

            // Redis에서 제거
            removeFromRedisCache(ipAddress);

            log.info("IP unbanned: {}", ipAddress);
        }
    }

    /**
     * IP가 차단되었는지 확인 (Redis 먼저 확인, 없으면 DB 조회)
     */
    public boolean isBanned(String ipAddress) {
        // Redis 캐시 확인
        Boolean isCached = redisTemplate.opsForSet().isMember(BANNED_IP_KEY, ipAddress);
        if (Boolean.TRUE.equals(isCached)) {
            return true;
        }

        // DB 확인
        Optional<IPBanEntity> banEntity = ipBanRepository.findActiveByIpAddress(ipAddress);
        if (banEntity.isPresent() && banEntity.get().isBanned()) {
            // Redis에 캐시 추가
            addToRedisCache(ipAddress);
            return true;
        }

        return false;
    }

    /**
     * 차단된 IP 정보 조회
     */
    public Optional<IPBanEntity> getBanInfo(String ipAddress) {
        return ipBanRepository.findActiveByIpAddress(ipAddress);
    }

    /**
     * 모든 활성 차단 목록 조회
     */
    public List<IPBanEntity> getAllActiveBans() {
        return ipBanRepository.findAllActiveBans()
                .stream()
                .filter(IPBanEntity::isBanned)
                .collect(Collectors.toList());
    }

    /**
     * 만료된 차단 정리 (30분마다 실행)
     */
    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void cleanupExpiredBans() {
        try {
            LocalDateTime now = LocalDateTime.now();
            log.info("Scheduled Task: cleaning up expired IP bans at {}", now);

            List<IPBanEntity> expiredBans = ipBanRepository.findExpiredBans(now);

            for (IPBanEntity ban : expiredBans) {
                ban.setIsActive(false);
                ipBanRepository.save(ban);
                removeFromRedisCache(ban.getIpAddress());
            }

            log.info("{} expired IP bans deactivated", expiredBans.size());
        } catch (Exception e) {
            log.error("Error during cleanupExpiredBans: ", e);
        }
    }

    /**
     * Redis 캐시 초기화 (서버 시작 시 또는 수동 호출)
     */
    @Transactional
    public void rebuildCache() {
        log.info("Rebuilding IP ban cache from database");

        // 기존 캐시 삭제
        redisTemplate.delete(BANNED_IP_KEY);

        // DB에서 활성 차단 목록 가져와서 Redis에 추가
        List<IPBanEntity> activeBans = ipBanRepository.findAllActiveBans()
                .stream()
                .filter(IPBanEntity::isBanned)
                .collect(Collectors.toList());

        for (IPBanEntity ban : activeBans) {
            addToRedisCache(ban.getIpAddress());
        }

        log.info("Cache rebuilt with {} active IP bans", activeBans.size());
    }

    /**
     * Redis 캐시에 IP 추가
     */
    private void addToRedisCache(String ipAddress) {
        try {
            redisTemplate.opsForSet().add(BANNED_IP_KEY, ipAddress);
        } catch (Exception e) {
            log.error("Failed to add IP to Redis cache: {}", ipAddress, e);
        }
    }

    /**
     * Redis 캐시에서 IP 제거
     */
    private void removeFromRedisCache(String ipAddress) {
        try {
            redisTemplate.opsForSet().remove(BANNED_IP_KEY, ipAddress);
        } catch (Exception e) {
            log.error("Failed to remove IP from Redis cache: {}", ipAddress, e);
        }
    }

    /**
     * 차단 연장
     */
    @Transactional
    public void extendBan(String ipAddress, Long additionalMinutes) {
        IPBanEntity entity = ipBanRepository.findActiveByIpAddress(ipAddress)
                .orElseThrow(() -> new IllegalArgumentException("해당 IP에 대한 활성 차단이 없습니다: " + ipAddress));

        if (entity.getBanType() != IPBanEntity.BanType.TEMPORARY) {
            throw new IllegalArgumentException("영구 차단은 연장할 수 없습니다");
        }

        if (entity.getExpiresAt() == null) {
            throw new IllegalStateException("만료 시간이 설정되지 않은 차단입니다");
        }

        entity.setExpiresAt(entity.getExpiresAt().plusMinutes(additionalMinutes));
        ipBanRepository.save(entity);
        log.info("IP ban extended: {} | Additional minutes: {}", ipAddress, additionalMinutes);
    }

    /**
     * IP 활동 로그 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public List<IPActivityEntity> getIPActivities(
            String ipAddress,
            String activityType,
            Boolean isSuspicious,
            Integer hoursBack,
            Integer limit
    ) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack != null ? hoursBack : 24);
        List<IPActivityEntity> activities;

        // 조건에 따라 쿼리 선택
        if (ipAddress != null && activityType != null) {
            // IP + 활동 타입으로 조회
            IPActivityEntity.ActivityType type = IPActivityEntity.ActivityType.valueOf(activityType);
            activities = ipActivityRepository.findByIpAndType(ipAddress, type, since);
        } else if (ipAddress != null) {
            // 특정 IP만 조회
            activities = ipActivityRepository.findRecentActivitiesByIp(ipAddress, since);
        } else if (isSuspicious != null && isSuspicious) {
            // 의심스러운 활동만 조회
            activities = ipActivityRepository.findSuspiciousActivities(since);
        } else {
            // 전체 조회
            activities = ipActivityRepository.findRecentActivities(since);
        }

        // limit 적용
        int maxLimit = limit != null ? Math.min(limit, 1000) : 100;  // 최대 1000개
        return activities.stream()
                .limit(maxLimit)
                .collect(Collectors.toList());
    }

    /**
     * 의심스러운 IP 목록 조회 (집계)
     */
    @Transactional(readOnly = true)
    public List<com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary> getSuspiciousIPsSummary(Integer hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack != null ? hoursBack : 24);
        List<Object[]> results = ipActivityRepository.findSuspiciousIPsSummary(since);

        // 차단된 IP 목록 조회
        List<String> bannedIPs = ipBanRepository.findAllActive().stream()
                .map(IPBanEntity::getIpAddress)
                .collect(Collectors.toList());

        return results.stream()
                .map(row -> com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary.builder()
                        .ipAddress((String) row[0])
                        .suspiciousCount(((Number) row[1]).longValue())
                        .lastActivityTime((LocalDateTime) row[2])
                        .lastLoginId((String) row[3])
                        .isBanned(bannedIPs.contains(row[0]))
                        .build())
                .collect(Collectors.toList());
    }
}
