package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.TeamHistoryDto;
import com.mjsec.ctf.dto.TeamProfileDto;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.service.TeamService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final JwtService jwtService;

    //팀 프로필 확인
    @Operation(summary = "팀 프로필 조회", description = "현재 로그인한 사용자의 팀 프로필을 조회합니다.")
    @GetMapping("/profile")
    public ResponseEntity<SuccessResponse<TeamProfileDto>> getTeamProfile(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String loginId = jwtService.getLoginId(token);

        TeamProfileDto teamProfile = teamService.getTeamProfile(loginId);

        return ResponseEntity.ok().body(
                SuccessResponse.of(
                        ResponseMessage.GET_TEAM_PROFILE_SUCCESS,
                        teamProfile
                )
        );
    }

    //팀 히스토리 조회
    @Operation(summary = "팀 풀이 기록 조회", description = "현재 로그인한 사용자의 팀이 풀었던 문제 기록을 시간순으로 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<List<TeamHistoryDto>> getTeamHistory(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String loginId = jwtService.getLoginId(token);

        List<TeamHistoryDto> teamHistory = teamService.getTeamHistory(loginId);

        return ResponseEntity.ok(teamHistory);
    }
}


