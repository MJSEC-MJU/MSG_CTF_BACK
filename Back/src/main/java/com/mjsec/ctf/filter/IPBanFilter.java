package com.mjsec.ctf.filter;

import com.mjsec.ctf.domain.IPBanEntity;
import com.mjsec.ctf.service.IPBanService;
import com.mjsec.ctf.util.IPAddressUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class IPBanFilter implements Filter {

    private final IPBanService ipBanService;

    public IPBanFilter(IPBanService ipBanService) {
        this.ipBanService = ipBanService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 클라이언트 IP 추출
        String clientIP = IPAddressUtil.getClientIP(httpRequest);

        // IP 차단 여부 확인
        if (ipBanService.isBanned(clientIP)) {
            // 차단 정보 조회
            Optional<IPBanEntity> banInfo = ipBanService.getBanInfo(clientIP);

            if (banInfo.isPresent()) {
                IPBanEntity ban = banInfo.get();
                log.warn("Blocked request from banned IP: {} | Reason: {} | URI: {}",
                         clientIP, ban.getReason(), httpRequest.getRequestURI());

                // JSON 응답 생성
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");

                String jsonResponse = createBanResponse(ban);
                httpResponse.getWriter().write(jsonResponse);
                return;
            } else {
                // 캐시에는 있지만 DB에는 없는 경우 (드문 경우)
                log.warn("IP {} is in cache but not found in DB", clientIP);
            }
        }

        // 차단되지 않은 경우 다음 필터로 진행
        chain.doFilter(request, response);
    }

    /**
     * 차단 응답 JSON 생성
     */
    private String createBanResponse(IPBanEntity ban) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"errorCode\":\"IP_BANNED\",");
        json.append("\"message\":\"귀하의 IP 주소는 차단되었습니다.\",");
        json.append("\"reason\":\"").append(escapeJson(ban.getReason())).append("\",");
        json.append("\"banType\":\"").append(ban.getBanType()).append("\",");
        json.append("\"bannedAt\":\"").append(ban.getBannedAt()).append("\"");

        if (ban.getExpiresAt() != null) {
            json.append(",\"expiresAt\":\"").append(ban.getExpiresAt()).append("\"");
        }

        json.append("}");
        return json.toString();
    }

    /**
     * JSON 문자열 이스케이프
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
