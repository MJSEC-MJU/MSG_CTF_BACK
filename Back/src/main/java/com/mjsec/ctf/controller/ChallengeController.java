package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.service.ChallengeService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/challenges")
@Controller
@RequiredArgsConstructor
public class ChallengeController {
    private final ChallengeService challengeService;

    @Operation(summary = "모든 문제 조회", description = "모든 문제의 id와 points를 반환합니다.")
    @GetMapping("/all")
    public ResponseEntity<SuccessResponse<Page<ChallengeDto.Simple>>> getAllChallenges(Pageable pageable) {

        Page<ChallengeDto.Simple> challenges = challengeService.getAllChallenges(pageable);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_ALL_CHALLENGE_SUCCESS,
                        challenges
                )
        );
    }
}
