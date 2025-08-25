package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.PaymentHistoryEntity;
import com.mjsec.ctf.domain.PaymentTokenEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.PaymentTokenDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.PaymentHistoryRepository;
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

    private final PaymentTokenRepository paymentTokenRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;

    public PaymentService(PaymentTokenRepository paymentTokenRepository, PaymentHistoryRepository paymentHistoryRepository,
                          UserRepository userRepository) {

        this.paymentTokenRepository = paymentTokenRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
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

        if(user.getMileage() < mileageUsed){
            throw new RestApiException(ErrorCode.NOT_ENOUGH_MILEAGE);
        }
        else {
            user.setMileage(user.getMileage() - mileageUsed);
        }
        userRepository.save(user);

        PaymentHistoryEntity historyEntity = PaymentHistoryEntity.builder()
                .loginId(tokenEntity.getLoginId())
                .mileageUsed(mileageUsed)
                .createdAt(LocalDateTime.now())
                .build();

        paymentHistoryRepository.save(historyEntity);
        paymentTokenRepository.deleteByPaymentToken(paymentToken);
    }
}

