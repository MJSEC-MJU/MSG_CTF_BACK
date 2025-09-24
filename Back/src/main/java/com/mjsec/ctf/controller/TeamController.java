package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.TeamProfileDto;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.service.TeamService;
import com.mjsec.ctf.type.ResponseMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final JwtService jwtService;

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
}


