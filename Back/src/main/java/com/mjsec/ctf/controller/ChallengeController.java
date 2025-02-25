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
import org.springframework.web.bind.annotation.PathVariable;
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

        Page<ChallengeDto.Simple> challenges = challengeService.getAllChallengesOrderedById(pageable);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_ALL_CHALLENGE_SUCCESS,
                        challenges
                )
        );
    }
    
    @Operation(summary = "특정 문제 상세 조회", description = "해당 문제 id를 가진 문제의 상세 정보를 반환합니다.")
    @GetMapping("/{challengeId}")
    public ResponseEntity<SuccessResponse<ChallengeDto.Detail>> getDetailChallenge(@PathVariable Long challengeId){

        ChallengeDto.Detail challengeDetail = challengeService.getDetailChallenge(challengeId);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_CHALLENGE_DETAIL_SUCCESS,
                        challengeDetail
                )
        );
    }
}
