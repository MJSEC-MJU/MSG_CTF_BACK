package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.repository.ChallengeRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.service.ChallengeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 관리자 권한으로 Challenge 조회
@RestController
@RequestMapping("/api/admin/challenge")
@RequiredArgsConstructor
public class AdminChallengeController {

    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;

    @Operation(summary = "전체 문제 요약 조회", description = "관리자 권한으로 전체 문제 목록에서 문제 번호, 제목, 포인트, 카테고리를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> getChallengeSummary() {

        List<ChallengeEntity> challenges = challengeRepository.findAllByOrderByChallengeIdAsc(Pageable.unpaged()).getContent();

        List<Map<String, Object>> summaryList = challenges.stream().map(challenge -> {
            Map<String, Object> map = new HashMap<>();
            map.put("challengeId", challenge.getChallengeId());
            map.put("title", challenge.getTitle());
            map.put("points", challenge.getPoints());
            map.put("category", challenge.getCategory().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(summaryList);
    }

    @Operation(summary = "문제 상세 조회", description = "관리자 권한으로 특정 문제의 상세 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeDto.Detail> getChallengeDetail(@PathVariable Long challengeId) {

        ChallengeDto.Detail detail = challengeService.getDetailChallenge(challengeId);
        return ResponseEntity.ok(detail);
    }

}
