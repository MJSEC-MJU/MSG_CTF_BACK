package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.IPActivityEntity;
import com.mjsec.ctf.domain.IPBanEntity;
import com.mjsec.ctf.repository.IPActivityRepository;
import com.mjsec.ctf.repository.IPBanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

        Optional<IPBanEntity> existing = ipBanRepository.findByIpAddress(ipAddress);

        IPBanEntity banEntity;
        if (existing.isPresent()) {
            banEntity = existing.get();
            banEntity.setReason(reason);
            banEntity.setBanType(banType);
            banEntity.setBannedAt(LocalDateTime.now());
            banEntity.setIsActive(true);
            banEntity.setBannedByAdminId(adminId);
            banEntity.setBannedByAdminLoginId(adminLoginId);
        } else {
            banEntity = new IPBanEntity();
            banEntity.setIpAddress(ipAddress);
            banEntity.setReason(reason);
            banEntity.setBanType(banType);
            banEntity.setBannedAt(LocalDateTime.now());
            banEntity.setIsActive(true);
            banEntity.setBannedByAdminId(adminId);
            banEntity.setBannedByAdminLoginId(adminLoginId);
        }

        if (banType == IPBanEntity.BanType.TEMPORARY && durationMinutes != null) {
            banEntity.setExpiresAt(LocalDateTime.now().plusMinutes(durationMinutes));
        } else {
            banEntity.setExpiresAt(null);
        }

        IPBanEntity savedEntity = ipBanRepository.save(banEntity);
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
        ipBanRepository.findByIpAddress(ipAddress).ifPresent(entity -> {
            entity.setIsActive(false);
            ipBanRepository.save(entity);
            removeFromRedisCache(ipAddress);
            log.info("IP unbanned: {}", ipAddress);
        });
    }

    /**
     * IP가 차단되었는지 확인 (Redis 먼저 확인, 없으면 DB 조회)
     */
    public boolean isBanned(String ipAddress) {
        Boolean isCached = redisTemplate.opsForSet().isMember(BANNED_IP_KEY, ipAddress);
        if (Boolean.TRUE.equals(isCached)) return true;

        Optional<IPBanEntity> banEntity = ipBanRepository.findActiveByIpAddress(ipAddress);
        if (banEntity.isPresent() && banEntity.get().isBanned()) {
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

        redisTemplate.delete(BANNED_IP_KEY);

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
     * IP 활동 로그 조회 (관리자용) — 동적 필터 (ip/activityType/isSuspicious)
     */
    @Transactional(readOnly = true)
    public List<IPActivityEntity> getIPActivities(
            String ipAddress,
            String activityType,
            Boolean isSuspicious,
            Integer hoursBack,
            Integer limit
    ) {
        LocalDateTime since = LocalDateTime.now().minusHours(
                (hoursBack == null || hoursBack < 0) ? 24 : hoursBack
        );
        int size = (limit == null) ? 100 : Math.min(Math.max(limit, 1), 1000);
        Pageable pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "activityTime"));

        IPActivityEntity.ActivityType typeEnum = parseActivityType(activityType);
        String ip = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : null;

        log.debug("[IPACT] filters -> ip={}, type={}, suspicious={}, since={}, limit={}",
                ip, typeEnum, isSuspicious, since, size);

        // 리포지토리의 동적 검색 메서드로 단일 경로 처리
        return ipActivityRepository.searchActivities(since, ip, typeEnum, isSuspicious, pageable);
    }

    private IPActivityEntity.ActivityType parseActivityType(String s) {
        if (s == null || s.isBlank()) return null;
        for (IPActivityEntity.ActivityType value : IPActivityEntity.ActivityType.values()) {
            if (value.name().equalsIgnoreCase(s.trim())) {
                return value;
            }
        }
        log.warn("[IPACT] Unknown activityType string '{}'; ignoring type filter.", s);
        return null; // 모르는 값이면 타입 필터 미적용
    }

    /**
     * 의심스러운 IP 목록 조회 (집계)
     */
    @Transactional(readOnly = true)
    public List<com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary> getSuspiciousIPsSummary(Integer hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack != null ? hoursBack : 24);
        List<Object[]> results = ipActivityRepository.findSuspiciousIPsSummary(since);

        Set<String> bannedIPs = ipBanRepository.findAllActiveBans().stream()
                .map(IPBanEntity::getIpAddress)
                .collect(Collectors.toSet());

        return results.stream()
                .map(row -> com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary.builder()
                        .ipAddress((String) row[0])
                        .suspiciousCount(((Number) row[1]).longValue())
                        .lastActivityTime((LocalDateTime) row[2])
                        .lastLoginId((String) row[3])
                        .lastActivityType(row[4] != null ? row[4].toString() : null)
                        .lastDetails((String) row[5])
                        .isBanned(row[0] != null && bannedIPs.contains((String) row[0]))
                        .build())
                .collect(Collectors.toList());
    }
}
