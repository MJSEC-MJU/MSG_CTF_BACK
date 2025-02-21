// AuthCodeService.java - 인증 코드 생성 및 저장 (이메일 인증용)
package com.mjsec.ctf.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthCodeService {

    private final Map<String, String> authCodeStorage = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public String generateAndStoreCode(String email) {
        String code = UUID.randomUUID().toString().substring(0, 6); // 6자리 코드 생성
        authCodeStorage.put(email, code);

        // 5분 후 인증 코드 삭제
        scheduler.schedule(() -> {
            authCodeStorage.remove(email);
            log.info("인증 코드 만료: {}", email);
        }, 5, TimeUnit.MINUTES);

        return code;
    }

    public boolean verifyCode(String email, String code) {
        String storedCode = authCodeStorage.get(email);
        return storedCode != null && storedCode.equals(code);
    }
}