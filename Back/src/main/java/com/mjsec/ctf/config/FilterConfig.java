package com.mjsec.ctf.config;

import com.mjsec.ctf.filter.AccessControlFilter;
import com.mjsec.ctf.service.ContestConfigService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    private final ContestConfigService contestConfigService;

    public FilterConfig(ContestConfigService contestConfigService) {
        this.contestConfigService = contestConfigService;
    }

    @Bean
    public FilterRegistrationBean<AccessControlFilter> accessControlFilter() {

        FilterRegistrationBean<AccessControlFilter> registrationBean = new FilterRegistrationBean<>();

        registrationBean.setFilter(new AccessControlFilter(contestConfigService));
        registrationBean.addUrlPatterns("/*");

        return registrationBean;
    }
}
