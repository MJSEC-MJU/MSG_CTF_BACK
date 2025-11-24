package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.PaymentTokenEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.TeamPaymentHistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.PaymentTokenDto;
import com.mjsec.ctf.dto.TeamPaymentHistoryDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.TeamPaymentHistoryRepository;
import com.mjsec.ctf.repository.PaymentTokenRepository;
import com.mjsec.ctf.repository.TeamRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {


    private final TeamService teamService;
    private final PaymentTokenRepository paymentTokenRepository;
    private final UserRepository userRepository;
    private final TeamPaymentHistoryRepository teamPaymentHistoryRepository;
    private final TeamRepository teamRepository;

    public PaymentService(TeamService teamService, PaymentTokenRepository paymentTokenRepository,
                          UserRepository userRepository, TeamPaymentHistoryRepository teamPaymentHistoryRepository,
                          TeamRepository teamRepository) {

        this.teamService = teamService;
        this.paymentTokenRepository = paymentTokenRepository;
        this.userRepository = userRepository;
        this.teamPaymentHistoryRepository = teamPaymentHistoryRepository;
        this.teamRepository = teamRepository;
    }

    public PaymentTokenDto createPaymentToken(String loginId) {

        PaymentTokenEntity paymentTokenEntity = PaymentTokenEntity.builder()
                .paymentToken(UUID.randomUUID().toString())
                .loginId(loginId)
                .expiry(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        paymentTokenRepository.save(paymentTokenEntity);

        return PaymentTokenDto.builder()
                .paymentTokenId(paymentTokenEntity.getPaymentTokenId())
                .token(paymentTokenEntity.getPaymentToken())
                .loginId(paymentTokenEntity.getLoginId())
                .expiry(paymentTokenEntity.getExpiry())
                .createdAt(paymentTokenEntity.getCreatedAt())
                .build();
    }

    @Transactional
    public void processPayment(String loginId, String paymentToken, int mileageUsed) {

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        PaymentTokenEntity tokenEntity = paymentTokenRepository.findByPaymentToken(paymentToken)
                .orElseThrow(() -> new RestApiException(ErrorCode.INVALID_TOKEN));

        if (tokenEntity.getExpiry().isBefore(LocalDateTime.now())) {
            throw new RestApiException(ErrorCode.TOKEN_EXPIRED);
        }

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }
        else {
            boolean success = teamService.useTeamMileage(
                    user.getCurrentTeamId(),
                    mileageUsed,
                    user.getUserId()
            );

            if (!success) {
                throw new RestApiException(ErrorCode.NOT_ENOUGH_MILEAGE);
            }
        }

        paymentTokenRepository.deleteByPaymentToken(paymentToken);
    }

    // 팀의 결제 히스토리 조회
    public List<TeamPaymentHistoryDto> getTeamPaymentHistory(String loginId) {
        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        List<TeamPaymentHistoryEntity> histories = teamPaymentHistoryRepository
                .findByTeamIdOrderByCreatedAtDesc(user.getCurrentTeamId());

        return histories.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 모든 결제 히스토리 조회 (관리자용)
    public List<TeamPaymentHistoryDto> getAllPaymentHistory() {
        List<TeamPaymentHistoryEntity> histories = teamPaymentHistoryRepository
                .findAllByOrderByCreatedAtDesc();

        return histories.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // 결제 철회 (관리자용)
    @Transactional
    public void refundPayment(Long paymentHistoryId) {
        TeamPaymentHistoryEntity history = teamPaymentHistoryRepository.findById(paymentHistoryId)
                .orElseThrow(() -> new RestApiException(ErrorCode.PAYMENT_HISTORY_NOT_FOUND));

        TeamEntity team = teamRepository.findById(history.getTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        // 마일리지 환불
        team.addMileage(history.getMileageUsed());
        teamRepository.save(team);

        // 히스토리 삭제
        teamPaymentHistoryRepository.delete(history);

        log.info("Payment refunded: paymentHistoryId={}, teamId={}, mileageRefunded={}",
                paymentHistoryId, history.getTeamId(), history.getMileageUsed());
    }

    // Entity -> DTO 변환
    private TeamPaymentHistoryDto convertToDto(TeamPaymentHistoryEntity entity) {
        TeamEntity team = teamRepository.findById(entity.getTeamId())
                .orElse(null);
        UserEntity user = userRepository.findById(entity.getRequesterUserId())
                .orElse(null);

        return TeamPaymentHistoryDto.builder()
                .teamPaymentHistoryId(entity.getTeamPaymentHistoryId())
                .teamId(entity.getTeamId())
                .teamName(team != null ? team.getTeamName() : "Unknown")
                .requesterUserId(entity.getRequesterUserId())
                .requesterLoginId(user != null ? user.getLoginId() : "Unknown")
                .mileageUsed(entity.getMileageUsed())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

