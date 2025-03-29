package com.mjsec.ctf.config;

import com.mjsec.ctf.filter.AccessControlFilter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    // 대회 시작 시간과 종료 시간 설정
    @Bean
    public FilterRegistrationBean<AccessControlFilter> accessControlFilter() {

        FilterRegistrationBean<AccessControlFilter> registrationBean = new FilterRegistrationBean<>();

        ZonedDateTime startTime = ZonedDateTime.of(
                // 2025년 3월 29일 오전 10시
                LocalDateTime.of(2025, 3, 29, 10, 0),
                ZoneId.of("Asia/Seoul")
        );

        ZonedDateTime endTime = ZonedDateTime.of(
                // 2025년 3월 29일 오후 10시
                LocalDateTime.of(2025, 3, 29, 22, 0),
                ZoneId.of("Asia/Seoul")
        );

        registrationBean.setFilter(new AccessControlFilter(startTime, endTime));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }
}
