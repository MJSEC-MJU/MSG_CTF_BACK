package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.type.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts.SIG;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtService {

    private SecretKey secretKey;
    private RefreshRepository refreshRepository;

    @Autowired
    public JwtService(@Value("${spring.jwt.secret}") String secret){

        secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), SIG.HS256.key().build().getAlgorithm());
    }

    public String getEmail(String token){

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("email", String.class);
    }

    public List<String> getRoles(String token) {

        List<?> roles = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("roles", List.class);

        if (roles != null) {
            return roles.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }


    public String getLoginId(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("loginId", String.class);
    }
    public Boolean isExpired(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
    }

    public String getTokenType(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("tokenType", String.class);
    }

    public Date getExpirationDate(String token) {
        Date expDate = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
        log.info("Extracted Expiration Date from JWT: {}", expDate);
        return expDate;
    }

    /**
     * JWT 생성
     *
     * @param tokenType : 토큰 타입(ACCESS 토큰 / REFRESH 토큰)
     * @param loginId : 유저의 로그인 id
     * @param roles : 유저의 권한 -> 일단 Enum 타입으로 수정함.
     * @param expiredMs : 만료 시점
     * @return JWT
     */
    public String createJwt(String tokenType, String loginId, List<String> roles, Long expiredMs) {

        return Jwts.builder()
                .claim("tokenType", tokenType)
                .claim("loginId", loginId)
                .claim("roles", roles)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }

    public Map<String, String> reissueTokens(String refreshToken) {

        if(isExpired(refreshToken)){
            throw new IllegalArgumentException("refresh token expired");
        }

        String tokenType = getTokenType(refreshToken);
        if(!tokenType.equals("refreshToken")) {
            throw new IllegalArgumentException("invalid refresh token");
        }

        if(!refreshRepository.existsByRefresh(refreshToken)){
            throw new IllegalArgumentException("invalid refresh token");
        }

        String loginId = getLoginId(refreshToken);
        List<String> roles = getRoles(refreshToken);

        String newAccessToken = createJwt("accessToken", loginId, roles, 3_600_000L); // 1시간
        String newRefreshToken = createJwt("refreshToken", loginId, roles, 86_400_000L); // 24시간

        refreshRepository.deleteByRefresh(refreshToken);
        addRefreshEntity(loginId, newRefreshToken, 43200000L);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", newRefreshToken);

        return tokens;
    }

    private void addRefreshEntity(String loginId, String refresh, Long expiredMs){

        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.setLoginId(loginId);
        refreshEntity.setRefresh(refresh);
        refreshEntity.setExpiration(date.toString());

        refreshRepository.save(refreshEntity);
    }
}