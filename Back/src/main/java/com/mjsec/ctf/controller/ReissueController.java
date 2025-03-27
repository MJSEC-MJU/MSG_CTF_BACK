package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.RefreshEntity;
import com.mjsec.ctf.repository.RefreshRepository;
import com.mjsec.ctf.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Date;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ResponseBody
public class ReissueController {

    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;

    public ReissueController(JwtService jwtService, RefreshRepository refreshRepository) {

        this.jwtService = jwtService;
        this.refreshRepository = refreshRepository;
    }

    //토큰 재생성
    @PostMapping("/api/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {

            if (cookie.getName().equals("refreshToken")) {

                refreshToken = cookie.getValue();
            }
        }

        if (refreshToken== null) {
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        try {
            jwtService.isExpired(refreshToken);
        } catch (ExpiredJwtException e) {
            return new ResponseEntity<>("refresh token expired", HttpStatus.BAD_REQUEST);
        }

        String tokenType = jwtService.getTokenType(refreshToken);

        if (!tokenType.equals("refreshToken")) {
            return new ResponseEntity<>("invalid refresh token ", HttpStatus.BAD_REQUEST);
        }

        Boolean isExist = refreshRepository.existsByRefresh(refreshToken);
        if (!isExist) {
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        String loginId = jwtService.getLoginId(refreshToken);
        List<String> roles = jwtService.getRoles(refreshToken);

        String newAccess = jwtService.createJwt("accessToken", loginId, roles, 3_600_000L);
        String newRefresh = jwtService.createJwt("refreshToken",loginId,roles,43_200_000L);

        refreshRepository.deleteByRefresh(refreshToken);
        addRefreshEntity(loginId, newRefresh, 86400000L);

        response.setHeader("Authorization", "Bearer " + newAccess);
        response.addCookie(createCookie("refreshToken",newRefresh));

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private Cookie createCookie(String key, String value) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24*60*60);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        return cookie;
    }

    private void addRefreshEntity(String loginId, String refresh, Long expiredMs) {

        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.setLoginId(loginId);
        refreshEntity.setRefresh(refresh);
        refreshEntity.setExpiration(date.toString());

        refreshRepository.save(refreshEntity);
    }
}
