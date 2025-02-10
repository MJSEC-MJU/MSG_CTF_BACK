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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.List;

@Controller
@ResponseBody
public class ReissueController {

    private final JwtService jwtService;
    private final RefreshRepository refreshRepository;

    public ReissueController(JwtService jwtService, RefreshRepository refreshRepository) {

        this.jwtService = jwtService;
        this.refreshRepository = refreshRepository;
    }

    //토큰 재생성
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        //get refresh token
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {

            if (cookie.getName().equals("refreshToken")) {

                refreshToken = cookie.getValue();
            }
        }

        if (refreshToken== null) {

            //response status code
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        //expired check
        try {
            jwtService.isExpired(refreshToken);
        } catch (ExpiredJwtException e) {

            //response status code
            return new ResponseEntity<>("refresh token expired", HttpStatus.BAD_REQUEST);
        }

        // 토큰이 refresh인지 확인 (발급시 페이로드에 명시)
        String tokenType = jwtService.getTokenType(refreshToken);

        if (!tokenType.equals("refreshToken")) {

            //response status code
            return new ResponseEntity<>("invalid refresh token ", HttpStatus.BAD_REQUEST);
        }
        //DB에 저장되어 있는지 확인
        Boolean isExist = refreshRepository.existsByRefresh(refreshToken);
        if (!isExist) {
            //response body
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        String loginId = jwtService.getLoginId(refreshToken);
        List<String> roles = jwtService.getRoles(refreshToken);

        //make new JWT
        String newAccess = jwtService.createJwt("accessToken", loginId, roles, 3600000L);
        String newRefresh = jwtService.createJwt("refreshToken",loginId,roles,43200000L);

        //Refresh 토큰 저장 DB에 기존의 Refresh 토큰 삭제 후 새 Refresh 토큰 저장
        refreshRepository.deleteByRefresh(refreshToken);
        addRefreshEntity(loginId, newRefresh, 86400000L);

        //response
        response.setHeader("Authorization", "Bearer " + newAccess);
        response.addCookie(createCookie("refreshToken",newRefresh));

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private Cookie createCookie(String key, String value) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24*60*60);
        //cookie.setSecure(true);
        //cookie.setPath("/");
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
