package com.mjsec.ctf.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

public class AccessControlFilter implements Filter {

    private final ZonedDateTime startTime;
    private final ZonedDateTime endTime;
    private final Pattern allowedBeforeStartPattern;
    private final Pattern submitPattern;

    public AccessControlFilter(ZonedDateTime startTime, ZonedDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.allowedBeforeStartPattern = Pattern
                .compile("^/api/users/(sign-in|sign-up|logout|check-id|check-email|send-code|verify-code)$");
        this.submitPattern = Pattern
                .compile("^/api/challenges/\\d+/submit$");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String requestURI = httpRequest.getRequestURI();

        /*
        if(now.isBefore(startTime) && !allowedBeforeStartPattern.matcher(requestURI).matches()){
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("This site is not available until " + startTime);
            return;
        }

        if(now.isAfter(endTime) && submitPattern.matcher(requestURI).matches()) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write("Submit is not allowed after " + endTime);
            return;
        }
        */

        chain.doFilter(request, response);
    }
}
