package com.mjsec.ctf.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjsec.ctf.domain.IPBanEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AlertService {

    private final boolean enabled;
    private final String endpoint;
    private final String apiKey;
    private final String environment; // 로그에만 사용
    private final HttpClient client;
    private final ObjectMapper om;

    public AlertService(
            @Value("${ctf.alert.enabled:true}") boolean enabled,
            @Value("${ctf.alert.endpoint:}") String endpoint,
            @Value("${ctf.alert.api-key:}") String apiKey,
            @Value("${ctf.alert.environment:prod}") String environment,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.environment = environment;
        this.client = HttpClient.newBuilder()
                // h2c 업그레이드 등으로 인한 바디 유실 방지
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(1500))
                .build();
        this.om = objectMapper;
    }

    @PostConstruct
    public void logAlertConfig() {
        String hostInfo = "-";
        try {
            URI u = URI.create(endpoint);
            String host = (u.getHost() != null ? u.getHost() : "-");
            int port = (u.getPort() == -1 ? ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80) : u.getPort());
            hostInfo = u.getScheme() + "://" + host + ":" + port + u.getPath();
        } catch (Exception ignore) {}
        log.info("[AlertService] init enabled={} endpoint='{}' apiKeyPresent={} environment={}",
                enabled, hostInfo, (apiKey != null && !apiKey.isBlank()), environment);
    }

    private boolean isReady() {
        boolean ok = enabled
                && endpoint != null && !endpoint.isBlank()
                && apiKey != null && !apiKey.isBlank();
        if (!ok) {
            log.warn("[AlertService] not ready -> enabled={}, endpointEmpty={}, apiKeyEmpty={}",
                    enabled, (endpoint == null || endpoint.isBlank()), (apiKey == null || apiKey.isBlank()));
        }
        return ok;
    }

    public void notifyIpBanned(IPBanEntity ban, String adminLoginId) {
        if (!isReady()) return;
        try {
            log.info("[AlertService] notifyIpBanned ip={} type={} reason='{}' admin={} bannedAt={} expiresAt={}",
                    ban.getIpAddress(), ban.getBanType(), ban.getReason(),
                    (adminLoginId != null ? adminLoginId : "AUTO_BAN_SYSTEM"),
                    ban.getBannedAt(), ban.getExpiresAt());

            Map<String, Object> payload = new LinkedHashMap<>();
            //  디코봇이 기대하는 키와 값
            payload.put("ipAddress", ban.getIpAddress());
            payload.put("reason", ban.getReason());
            payload.put("banType", String.valueOf(ban.getBanType())); // "TEMPORARY" | "PERMANENT"
            payload.put("bannedAt", toIso(ban.getBannedAt()));
            payload.put("expiresAt", toIso(ban.getExpiresAt()));
            payload.put("bannedByAdminLoginId", adminLoginId != null ? adminLoginId : "AUTO_BAN_SYSTEM");
            payload.put("durationMinutes", calcDurationMinutes(ban.getBannedAt(), ban.getExpiresAt()));

            fireAndForget(payload);
        } catch (Exception e) {
            log.warn("notifyIpBanned failed: {}", e.toString());
        }
    }

    private static String toIso(LocalDateTime t) {
        return (t == null) ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static Long calcDurationMinutes(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        return ChronoUnit.MINUTES.between(from, to);
    }

    private void fireAndForget(Map<String, Object> payload) throws Exception {
        String body = om.writeValueAsString(payload);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(3000))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("x-api-key", apiKey) // 디코봇과 합의된 헤더
                // 'Connection' 등 hop-by-hop 헤더는 금지 (IAE 발생 원인)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        // 디버그: 전송 바이트 길이/바디
        log.debug("[ALERT -> {}] len={} body={}", environment, bytes.length, body);

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    int code = resp.statusCode();
                    String respBody = resp.body();
                    log.info("[AlertService] POST {} -> status={} respLen={}",
                            endpoint, code, (respBody == null ? 0 : respBody.length()));
                    if (code >= 300) {
                        log.warn("Alert HTTP {} (env={}) body='{}'", code, environment, respBody);
                    }
                })
                .exceptionally(ex -> {
                    log.warn("Alert HTTP failed once (will retry): {}", ex.toString());
                    try {
                        var retryResp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        log.info("[AlertService] retry status={} respLen={}",
                                retryResp.statusCode(),
                                (retryResp.body() == null ? 0 : retryResp.body().length()));
                        if (retryResp.statusCode() >= 300) {
                            log.warn("Alert retry got {} body='{}'", retryResp.statusCode(), retryResp.body());
                        }
                    } catch (Exception e2) {
                        log.warn("Alert retry failed: {}", e2.toString());
                    }
                    return null;
                });
    }
}
