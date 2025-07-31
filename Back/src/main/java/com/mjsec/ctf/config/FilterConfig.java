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

    @Bean
    public FilterRegistrationBean<AccessControlFilter> accessControlFilter() {

        FilterRegistrationBean<AccessControlFilter> registrationBean = new FilterRegistrationBean<>();

        ZonedDateTime startTime = ZonedDateTime.of(
                LocalDateTime.of(2025, 3, 29, 10, 0),
                ZoneId.of("Asia/Seoul")
        );

        ZonedDateTime endTime = ZonedDateTime.of(
                LocalDateTime.of(2025, 7, 29, 22, 0),
                ZoneId.of("Asia/Seoul")
        );

        registrationBean.setFilter(new AccessControlFilter(startTime, endTime));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }
}
