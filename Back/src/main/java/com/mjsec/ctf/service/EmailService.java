// EmailService.java - 이메일 전송 서비스 (이메일 인증용)
package com.mjsec.ctf.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("학교 이메일 인증 코드");
        message.setText("인증 코드는 다음과 같습니다: " + code);

        mailSender.send(message);
        log.info("이메일 전송 완료: {}", toEmail);
    }
}