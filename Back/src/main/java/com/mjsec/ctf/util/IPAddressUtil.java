package com.mjsec.ctf.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IPAddressUtil {

    private static final String[] IP_HEADER_CANDIDATES = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };

    /**
     * HttpServletRequest에서 실제 클라이언트 IP 주소를 추출합니다.
     * 프록시나 로드밸런서를 거친 경우에도 실제 IP를 얻을 수 있습니다.
     */
    public static String getClientIP(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }

        String ip = null;

        // 프록시 헤더들을 순회하며 IP 찾기
        for (String header : IP_HEADER_CANDIDATES) {
            ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                break;
            }
        }

        // 헤더에서 찾지 못한 경우 RemoteAddr 사용
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For는 여러 IP가 쉼표로 구분되어 있을 수 있음 (첫 번째가 실제 클라이언트 IP)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // IPv6 localhost를 IPv4로 변환
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }

        log.debug("Extracted client IP: {}", ip);

        return ip != null ? ip : "0.0.0.0";
    }

    /**
     * IP 주소가 유효한 형식인지 검증
     */
    public static boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // IPv4 정규표현식
        String ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        // IPv6 간단 검증
        String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";

        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern);
    }

    /**
     * 로컬 IP인지 확인 (내부 네트워크 포함)
     */
    public static boolean isLocalIP(String ip) {
        if (ip == null) {
            return false;
        }

        return ip.startsWith("127.") ||           // localhost
               ip.startsWith("192.168.") ||       // 사설 네트워크 (Class C)
               ip.startsWith("10.") ||            // 사설 네트워크 (Class A)
               ip.startsWith("172.16.") ||        // 사설 네트워크 (Class B) - 172.16.0.0 ~ 172.31.255.255
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||        // Docker 기본 네트워크
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.") ||
               ip.equals("::1") ||                // IPv6 localhost
               ip.equals("0:0:0:0:0:0:0:1");     // IPv6 localhost
    }
}
