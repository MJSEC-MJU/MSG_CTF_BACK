package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.challenge.ChallengeDto;
import com.mjsec.ctf.dto.user.UserDTO;
import com.mjsec.ctf.service.ChallengeService;
import com.mjsec.ctf.service.FileService;
import com.mjsec.ctf.service.UserService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ChallengeService challengeService;

    @Operation(summary = "문제 생성", description = "관리자 권한으로 문제를 생성합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create/challenge")
    public ResponseEntity<SuccessResponse<Void>> createChallenge(
            @RequestPart("file") MultipartFile file,
            @RequestPart("challenge") ChallengeDto challengeDto) throws IOException {

        challengeService.createChallenge(file, challengeDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.CREATE_CHALLENGE_SUCCESS
                )
        );
    }
/*
    @Operation(summary = "문제 수정", description = "관리자 권한으로 문제를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/change/challenge")
    public ResponseEntity<SuccessRespons

 */
}