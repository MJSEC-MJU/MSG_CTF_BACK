package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.TeamSignatureUnlockRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/signature")
@RequiredArgsConstructor
public class SignatureExtraController {

    private final TeamSignatureUnlockRepository unlockRepo;
    private final UserRepository userRepo;

    private String currentLoginId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Operation(summary = "내 팀이 언락한 시그니처 챌린지 ID 목록")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/unlocked")
    public ResponseEntity<SignatureDto.UnlockedListResponse> listUnlockedForMyTeam() {
        var user = userRepo.findByLoginId(currentLoginId())
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        var ids = unlockRepo.findByTeamId(user.getCurrentTeamId()).stream()
                .map(u -> u.getChallengeId())
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SignatureDto.UnlockedListResponse.builder()
                        .teamId(user.getCurrentTeamId())
                        .challengeIds(ids)
                        .build()
        );
    }
}
