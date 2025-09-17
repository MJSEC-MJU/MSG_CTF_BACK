package com.mjsec.ctf.config;

import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminUserInitializer {

    @Value("${SPRING_SECURITY_USER_NAME}")
    private String adminUsername;

    @Value("${SPRING_SECURITY_USER_PASSWORD}")
    private String adminPassword;

    @Bean
    public CommandLineRunner createAdminUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByLoginId(adminUsername)) {
                UserEntity admin = UserEntity.builder()
                        .loginId(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .email("admin@example.com") 
                        .univ("Admin Univ")       
                        .role("ROLE_ADMIN")
                        .totalPoint(0)
                        .mileage(0)
                        .build();
                userRepository.save(admin);
                System.out.println("관리자 계정 생성 완료: " + adminUsername);
            }
        };
    }
}
