package com.mjsec.ctf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureCheckerService {

    private final ObjectMapper mapper;         // 스프링이 주입해주는 ObjectMapper 사용
    private final ResourceLoader resourceLoader;

    /**
     * code-json-path는 아래 3가지 모두 지원:
     *  - classpath:data/codes.json  (권장)
     *  - file:/D:/path/to/codes.json
     *  - D:/path/to/codes.json      (순수 OS 경로)
     */
    @Value("${app.code-json-path:classpath:data/codes.json}")
    private String codeJsonPath;

    // club -> unique codes (6자리 영숫자)
    private final Map<String, Set<String>> dict = new HashMap<>();

    // 영숫자 6자리 (대소문자 모두 허용)
    private static final Pattern SIX = Pattern.compile("^[A-Z0-9]{6}$");

    @PostConstruct
    void init() throws IOException {
        loadCodebook();
        log.info("Loaded code dictionary for clubs: {}", dict.keySet());
    }

    /**
     * 필요시 외부에서 수동 리로드 가능하게 별도 함수로 분리
     */
    public synchronized void reload() throws IOException {
        dict.clear();
        loadCodebook();
        log.info("Reloaded code dictionary. clubs={}", dict.keySet());
    }

    private void loadCodebook() throws IOException {
        Resource res = resolve(codeJsonPath);
        if (!res.exists()) {
            throw new IllegalStateException("Code JSON not found: " + codeJsonPath);
        }
        try (InputStream in = res.getInputStream()) {
            // JSON 스키마: Map<String, Map<String, String>>
            // 예) { "MJSEC": {"0":"ABC123", ...}, "SecurityFirst": {...} }
            Map<String, Map<String, String>> root = mapper.readValue(
                    in, new TypeReference<>() {}
            );
            if (root == null) return;

            for (Map.Entry<String, Map<String, String>> e : root.entrySet()) {
                String club = e.getKey();
                Map<String, String> idxMap = e.getValue();
                if (idxMap == null || idxMap.isEmpty()) continue;

                Set<String> codes = new HashSet<>();
                for (String v : idxMap.values()) {
                    if (v != null && SIX.matcher(v).matches()) {
                        codes.add(v);
                    }
                }
                if (!codes.isEmpty()) {
                    dict.put(club, codes);
                }
            }
        }
    }

    /**
     * classpath:/ file:/ 순수경로 모두 처리
     */
    private Resource resolve(String location) {
        // 순수 OS 경로면 resourceLoader가 "file:" 접두사 없이도 읽어주지만,
        // Windows 경로에서 ':' 오류 방지를 위해 명시적 처리도 가능.
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length()));
        }
        return resourceLoader.getResource(location);
    }

    /**
     * 특정 club 하위 값들 중 value가 존재하는지 검사
     */
    public boolean exists(String club, String value, boolean ignoreCase) {
        if (club == null || value == null) return false;
        if (!SIX.matcher(value).matches()) return false;

        Set<String> codes = dict.get(club);
        if (codes == null || codes.isEmpty()) return false;

        if (ignoreCase) {
            String v = value.toLowerCase(Locale.ROOT);
            for (String c : codes) {
                if (c != null && c.length() == value.length()
                        && c.toLowerCase(Locale.ROOT).equals(v)) {
                    return true;
                }
            }
            return false;
        } else {
            for (String c : codes) {
                if (constantTimeEquals(c, value)) return true;
            }
            return false;
        }
    }

    // 타이밍 공격 대비: 길이가 고정(6)이므로 가벼움
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
