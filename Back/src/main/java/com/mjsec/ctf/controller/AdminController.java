package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.dto.user.UserDTO;
import com.mjsec.ctf.service.ChallengeService;
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
import java.util.stream.Collectors;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create/challenge-no-file")
    public ResponseEntity<SuccessResponse<Void>> createChallengeWithoutFile(
            @RequestBody @Valid ChallengeDto challengeDto) throws IOException {

        // 기존 서비스 메소드를 재사용하되, 파일을 null로 전달
        challengeService.createChallenge(null, challengeDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.CREATE_CHALLENGE_SUCCESS
                )
        );
    }

    @Operation(summary = "문제 수정", description = "관리자 권한으로 문제를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/update/challenge/{challengeId}")
    public ResponseEntity<SuccessResponse<Void>> updateChallenge(
            @PathVariable Long challengeId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart("challenge") ChallengeDto challengeDto) throws IOException {

        challengeService.updateChallenge(challengeId, file, challengeDto);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.UPDATE_CHALLENGE_SUCCESS
                )
        );
    }


    @Operation(summary = "문제 삭제", description = "관리자 권한으로 문제를 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/challenge/{challengeId}")
    public ResponseEntity<SuccessResponse<Void>> deleteChallenge(@PathVariable Long challengeId) {

        challengeService.deleteChallenge(challengeId);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.DELETE_CHALLENGE_SUCCESS
                )
        );
    }
    @Operation(summary = "회원 정보 변경 (관리자)", description = "관리자 권한으로 특정 회원의 정보를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/change/member/{userId}")
    public ResponseEntity<SuccessResponse<UserDTO.Response>> changeMember(
            @PathVariable Long userId,
            @RequestBody @Valid UserDTO.Update updateDto) {

        UserEntity updatedUser = userService.updateMember(userId, updateDto);
        UserDTO.Response responseDto = new UserDTO.Response(
                updatedUser.getUserId(),
                updatedUser.getEmail(),
                updatedUser.getLoginId(),
                updatedUser.getRole(),
                updatedUser.getTotalPoint(),
                updatedUser.getUniv(),
                updatedUser.getCreatedAt(),
                updatedUser.getUpdatedAt()
        );
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.UPDATE_SUCCESS, responseDto));
    }

    @Operation(summary = "회원 삭제제 (관리자)", description = "관리자 권한으로 회원 계정을 삭제제합니다. (관리자 계정도 삭제 가능)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/member/{userId}")
    public ResponseEntity<SuccessResponse<Void>> deleteMember(@PathVariable Long userId) {
        userService.deleteMember(userId);
        log.info("관리자에 의해 회원 {} 삭제 완료", userId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.DELETE_SUCCESS));
    }

    @Operation(summary = "회원 추가 (관리자)", description = "관리자 권한으로 이메일 인증 없이 새로운 회원 계정을 생성합니다. (관리자 계정도 추가 가능)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add/member")
    public ResponseEntity<SuccessResponse<Void>> addMember(@RequestBody @Valid UserDTO.SignUp newUser) {
        userService.adminSignUp(newUser);
        log.info("관리자에 의해 새로운 회원 추가 완료: {}", newUser.getLoginId());
        return ResponseEntity.status(201).body(SuccessResponse.of(ResponseMessage.SIGNUP_SUCCESS));
    }

    @Operation(summary = "전체 사용자 조회", description = "관리자 권한으로 전체 회원 목록을 JSON 형식으로 반환합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    @GetMapping("/member")
    public ResponseEntity<List<UserDTO.Response>> getAllMembers() {
        List<UserEntity> users = userService.getAllUsers();
        // UserEntity를 UserDTO.Response 형태로 변환 (필요에 따라 DTO 변환 로직 추가)
        List<UserDTO.Response> responseList = users.stream().map(user -> 
            new UserDTO.Response(user.getUserId(), user.getEmail(), user.getLoginId(), user.getRole(), user.getTotalPoint(), user.getUniv(), user.getCreatedAt(), user.getUpdatedAt())
        ).collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    @Operation(summary = "회원 정보 조회", description = "관리자 권한으로 특정 회원의 정보를 조회하여 수정을 위한 데이터를 반환합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/member/{userId}")
    public ResponseEntity<UserDTO.Response> getMember(@PathVariable Long userId) {
        UserEntity user = userService.getUserById(userId);
        UserDTO.Response responseDto = new UserDTO.Response(
                user.getUserId(),
                user.getEmail(),
                user.getLoginId(),
                user.getRole(),
                user.getTotalPoint(),
                user.getUniv(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        return ResponseEntity.ok(responseDto);
    }

    @Operation(summary = "관리자 권한 검증", description = "현재 인증된 사용자가 관리자임을 확인하는 API입니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/validate")
    public ResponseEntity<String> validateAdmin() {
        return ResponseEntity.ok("admin");
    }

    @Operation(summary = "점수 재계산", description = "관리자 권한으로 모든 유저의 점수를 재계산합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recalculate-points")
    public ResponseEntity<String> recalculatePoints() {
        try {
            log.info("Manual points recalculation started by admin");
            challengeService.updateTotalPoints();
            log.info("Manual points recalculation completed");
            return ResponseEntity.ok("점수 재계산 완료");
        } catch (Exception e) {
            log.error("점수 재계산 중 오류 발생: ", e);
            return ResponseEntity.status(500).body("점수 재계산 실패: " + e.getMessage());
        }
    }
}
