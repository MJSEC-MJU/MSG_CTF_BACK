package com.mjsec.ctf.service;

import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.type.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthCodeService {

    private final StringRedisTemplate redisTemplate;

    public AuthCodeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateAndStoreCode(String email) {
        String code = generateNumericCode(6);

        // Redis에 저장 (5분 동안 유효)
        redisTemplate.opsForValue().set("authCode:" + email, code, 5, TimeUnit.MINUTES);
        log.info("인증 코드 생성 및 저장: {} -> {}", email, code);

        return code;
    }

    public boolean verifyCode(String email, String code) {
        String key = "authCode:" + email;
        String storedCode = redisTemplate.opsForValue().get(key);
        String attemptKey = "attempts:" + email;

        // 인증 시도 횟수 확인
        Integer attempts = Optional.ofNullable(redisTemplate.opsForValue().get(attemptKey))
                .map(Integer::valueOf)
                .orElse(0);

        if (attempts >= 5) {
            redisTemplate.delete(key); // 인증 코드 삭제
            redisTemplate.delete(attemptKey);
            log.warn("인증 시도 횟수 초과: {}", email);
            throw new RestApiException(ErrorCode.AUTH_ATTEMPT_EXCEEDED);
        }

        if (storedCode != null && storedCode.equals(code)) {
            // 인증 성공
            redisTemplate.opsForValue().set("verified:" + email, "true", 30, TimeUnit.MINUTES);
            redisTemplate.delete(key); // 인증 코드 삭제
            redisTemplate.delete(attemptKey); // 시도 횟수 삭제
            log.info("이메일 인증 성공: {}", email);
            return true;
        } else {
            // 인증 실패 시 시도 횟수 증가
            redisTemplate.opsForValue().increment(attemptKey);
            redisTemplate.expire(attemptKey, 5, TimeUnit.MINUTES); // 5분 동안 시도 횟수 유지
            log.warn("이메일 인증 실패: {} (시도 횟수: {})", email, attempts + 1);
            return false;
        }
    }

    // 이메일 인증 여부 확인
    public boolean isEmailVerified(String email) {
        String verified = redisTemplate.opsForValue().get("verified:" + email);
        return "true".equals(verified);
    }

    // 6자리 숫자 코드 생성 메서드
    private String generateNumericCode(int length) {
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10)); // 0부터 9까지의 숫자 중 랜덤 선택
        }

        return code.toString();
    }
}