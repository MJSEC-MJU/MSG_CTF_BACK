package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.PaymentHistoryEntity;
import com.mjsec.ctf.domain.PaymentTokenEntity;
import com.mjsec.ctf.dto.PaymentTokenDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.PaymentHistoryRepository;
import com.mjsec.ctf.repository.PaymentTokenRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {

    private final PaymentTokenRepository tokenRepository;
    private final PaymentHistoryRepository historyRepository;

    public PaymentService(PaymentTokenRepository tokenRepository, PaymentHistoryRepository historyRepository) {

        this.tokenRepository = tokenRepository;
        this.historyRepository = historyRepository;
    }

    public PaymentTokenDto createPaymentToken(String loginId) {

        PaymentTokenEntity paymentTokenEntity = PaymentTokenEntity.builder()
                .paymentToken(UUID.randomUUID().toString())
                .loginId(loginId)
                .expiry(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        tokenRepository.save(paymentTokenEntity);

        return PaymentTokenDto.builder()
                .paymentTokenId(paymentTokenEntity.getPaymentTokenId())
                .token(paymentTokenEntity.getPaymentToken())
                .loginId(paymentTokenEntity.getLoginId())
                .expiry(paymentTokenEntity.getExpiry())
                .createdAt(paymentTokenEntity.getCreatedAt())
                .build();
    }

    @Transactional
    public void processPayment(String paymentToken, int mileageUsed) {

        PaymentTokenEntity tokenEntity = tokenRepository.findByPaymentToken(paymentToken)
                .orElseThrow(() -> new RestApiException(ErrorCode.INVALID_TOKEN));

        if (tokenEntity.getExpiry().isBefore(LocalDateTime.now())) {
            throw new RestApiException(ErrorCode.TOKEN_EXPIRED);
        }

        PaymentHistoryEntity historyEntity = PaymentHistoryEntity.builder()
                .loginId(tokenEntity.getLoginId())
                .mileageUsed(mileageUsed)
                .createdAt(LocalDateTime.now())
                .build();

        historyRepository.save(historyEntity);
        tokenRepository.deleteByPaymentToken(paymentToken);
    }
}

