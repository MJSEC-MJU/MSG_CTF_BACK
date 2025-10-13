package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ContestConfigEntity;
import com.mjsec.ctf.dto.ContestConfigDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ContestConfigRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContestConfigService {

    private final ContestConfigRepository contestConfigRepository;

    public ContestConfigService(ContestConfigRepository contestConfigRepository) {
        this.contestConfigRepository = contestConfigRepository;
    }

    public ContestConfigDto.Response getContestTime() {
        ContestConfigEntity config = contestConfigRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new RestApiException(ErrorCode.CONTEST_CONFIG_NOT_FOUND));

        ZonedDateTime currentTime = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        return ContestConfigDto.Response.fromEntity(config, currentTime);
    }

    @Transactional
    public ContestConfigDto updateContestTime(ContestConfigDto.Request request) {
        // 기존 활성화된 설정이 있으면 비활성화
        contestConfigRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .ifPresent(config -> {
                    config.setIsActive(false);
                    contestConfigRepository.save(config);
                });

        // 새로운 설정 생성
        ContestConfigEntity newConfig = ContestConfigEntity.builder()
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isActive(true)
                .build();

        ContestConfigEntity savedConfig = contestConfigRepository.save(newConfig);
        return ContestConfigDto.fromEntity(savedConfig);
    }

    public ContestConfigEntity getActiveConfig() {
        return contestConfigRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElse(null);
    }
}
