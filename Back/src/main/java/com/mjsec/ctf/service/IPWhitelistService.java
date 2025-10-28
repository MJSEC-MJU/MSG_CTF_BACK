package com.mjsec.ctf.service;

import com.mjsec.ctf.entity.IPWhitelistEntity;
import com.mjsec.ctf.repository.IPWhitelistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * IP 화이트리스트 관리 서비스
 * 화이트리스트에 추가된 IP는 자동 차단 시스템에서 제외됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IPWhitelistService {

    private final IPWhitelistRepository ipWhitelistRepository;

    /**
     * IP를 화이트리스트에 추가
     * @param ipAddress IP 주소
     * @param reason 화이트리스트 추가 사유
     * @param adminId 관리자 ID
     * @param adminLoginId 관리자 로그인 ID
     * @return 추가된 화이트리스트 엔티티
     */
    @Transactional
    public IPWhitelistEntity addToWhitelist(String ipAddress, String reason, Long adminId, String adminLoginId) {
        // 이미 존재하는지 확인
        Optional<IPWhitelistEntity> existing = ipWhitelistRepository.findByIpAddress(ipAddress);

        IPWhitelistEntity whitelistEntity;
        if (existing.isPresent()) {
            // 기존 화이트리스트 업데이트 (재활성화)
            whitelistEntity = existing.get();
            whitelistEntity.setReason(reason);
            whitelistEntity.setIsActive(true);
            whitelistEntity.setAddedByAdminId(adminId);
            whitelistEntity.setAddedByAdminLoginId(adminLoginId);
            whitelistEntity.setAddedAt(LocalDateTime.now());
            log.info("IP whitelist re-activated: {} | Reason: {} | By: {}", ipAddress, reason, adminLoginId);
        } else {
            // 새로운 화이트리스트 생성
            whitelistEntity = IPWhitelistEntity.builder()
                    .ipAddress(ipAddress)
                    .reason(reason)
                    .isActive(true)
                    .addedByAdminId(adminId)
                    .addedByAdminLoginId(adminLoginId)
                    .addedAt(LocalDateTime.now())
                    .build();
            log.info("IP whitelist added: {} | Reason: {} | By: {}", ipAddress, reason, adminLoginId);
        }

        return ipWhitelistRepository.save(whitelistEntity);
    }

    /**
     * IP를 화이트리스트에서 제거 (비활성화)
     * @param ipAddress IP 주소
     * @return 제거 성공 여부
     */
    @Transactional
    public boolean removeFromWhitelist(String ipAddress) {
        Optional<IPWhitelistEntity> existing = ipWhitelistRepository.findByIpAddress(ipAddress);

        if (existing.isPresent()) {
            IPWhitelistEntity entity = existing.get();
            entity.setIsActive(false);
            ipWhitelistRepository.save(entity);
            log.info("IP whitelist removed: {}", ipAddress);
            return true;
        }

        log.warn("IP whitelist not found for removal: {}", ipAddress);
        return false;
    }

    /**
     * IP가 화이트리스트에 있는지 확인 (활성 상태만)
     * @param ipAddress IP 주소
     * @return 화이트리스트 여부
     */
    public boolean isWhitelisted(String ipAddress) {
        return ipWhitelistRepository.isWhitelisted(ipAddress);
    }

    /**
     * 활성 화이트리스트 목록 조회
     * @return 활성 화이트리스트 목록
     */
    public List<IPWhitelistEntity> getActiveWhitelist() {
        return ipWhitelistRepository.findAllActive();
    }

    /**
     * 전체 화이트리스트 목록 조회 (비활성 포함)
     * @return 전체 화이트리스트 목록
     */
    public List<IPWhitelistEntity> getAllWhitelist() {
        return ipWhitelistRepository.findAllWhitelist();
    }

    /**
     * 특정 IP의 화이트리스트 정보 조회
     * @param ipAddress IP 주소
     * @return 화이트리스트 엔티티 (Optional)
     */
    public Optional<IPWhitelistEntity> getWhitelistInfo(String ipAddress) {
        return ipWhitelistRepository.findByIpAddress(ipAddress);
    }

    /**
     * IP 주소가 화이트리스트에 존재하는지 확인 (비활성 포함)
     * @param ipAddress IP 주소
     * @return 존재 여부
     */
    public boolean exists(String ipAddress) {
        return ipWhitelistRepository.existsByIpAddress(ipAddress);
    }
}
