package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.PaymentTokenEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.PaymentTokenDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.TeamPaymentHistoryRepository;
import com.mjsec.ctf.repository.PaymentTokenRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {


    private final TeamService teamService;
    private final PaymentTokenRepository paymentTokenRepository;
    private final UserRepository userRepository;

    public PaymentService(TeamService teamService, PaymentTokenRepository paymentTokenRepository,
                          UserRepository userRepository) {

        this.teamService = teamService;
        this.paymentTokenRepository = paymentTokenRepository;
        this.userRepository = userRepository;
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
}

