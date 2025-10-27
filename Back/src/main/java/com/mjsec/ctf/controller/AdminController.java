package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.IPBanEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.dto.ContestConfigDto;
import com.mjsec.ctf.dto.GrantMileageDto;
import com.mjsec.ctf.dto.IPBanDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.TeamPaymentHistoryDto;
import com.mjsec.ctf.dto.TeamSummaryDto;
import com.mjsec.ctf.dto.UserDto;
import com.mjsec.ctf.service.ChallengeService;
import com.mjsec.ctf.service.ContestConfigService;
import com.mjsec.ctf.service.IPBanService;
import com.mjsec.ctf.service.TeamService;
import com.mjsec.ctf.service.UserService;
import com.mjsec.ctf.type.ResponseMessage;
import com.mjsec.ctf.util.IPAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final ChallengeService challengeService;
    private final TeamService teamService;
    private final ContestConfigService contestConfigService;
    private final com.mjsec.ctf.service.PaymentService paymentService;
    private final IPBanService ipBanService;
    private final com.mjsec.ctf.service.IPWhitelistService ipWhitelistService;

    // -------------------------------
    // Challenge 관리
    // -------------------------------

    @Operation(summary = "문제 생성", description = "관리자 권한으로 문제를 생성합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/create/challenge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Void>> createChallenge(
            @RequestPart("file") MultipartFile file,
            @RequestPart("challenge") ChallengeDto challengeDto
    ) throws IOException {
        challengeService.createChallenge(file, challengeDto);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.CREATE_CHALLENGE_SUCCESS));
    }

    @Operation(summary = "문제 생성(파일 없이)", description = "관리자 권한으로 문제를 생성합니다. 첨부파일 없이 JSON만 보냅니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/create/challenge-no-file", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<Void>> createChallengeWithoutFile(
            @RequestBody @Valid ChallengeDto challengeDto
    ) throws IOException {
        challengeService.createChallenge(null, challengeDto);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.CREATE_CHALLENGE_SUCCESS));
    }

    @Operation(summary = "문제 수정", description = "관리자 권한으로 문제를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/update/challenge/{challengeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Void>> updateChallenge(
            @PathVariable Long challengeId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart("challenge") ChallengeDto challengeDto
    ) throws IOException {
        challengeService.updateChallenge(challengeId, file, challengeDto);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.UPDATE_CHALLENGE_SUCCESS));
    }

    @Operation(summary = "문제 삭제", description = "관리자 권한으로 문제를 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/challenge/{challengeId}")
    public ResponseEntity<SuccessResponse<Void>> deleteChallenge(@PathVariable Long challengeId) {
        challengeService.deleteChallenge(challengeId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.DELETE_CHALLENGE_SUCCESS));
    }

    // -------------------------------
    // User 관리
    // -------------------------------

    @Operation(summary = "회원 정보 변경 (관리자)", description = "관리자 권한으로 특정 회원의 정보를 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/change/member/{userId}")
    public ResponseEntity<SuccessResponse<UserDto.Response>> changeMember(
            @PathVariable Long userId,
            @RequestBody @Valid UserDto.Update updateDto
    ) {
        UserEntity updatedUser = userService.updateMember(userId, updateDto);
        UserDto.Response responseDto = new UserDto.Response(
                updatedUser.getUserId(),
                updatedUser.getEmail(),
                updatedUser.getLoginId(),
                updatedUser.getRole().name(),
                updatedUser.getTotalPoint(),
                updatedUser.getUniv(),
                updatedUser.getCreatedAt(),
                updatedUser.getUpdatedAt()
        );
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.UPDATE_SUCCESS, responseDto));
    }

    @Operation(summary = "회원 삭제 (관리자)", description = "관리자 권한으로 회원 계정을 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/delete/member/{userId}")
    public ResponseEntity<SuccessResponse<Void>> deleteMember(@PathVariable Long userId) {
        userService.deleteMember(userId);
        log.info("관리자에 의해 회원 {} 삭제 완료", userId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.DELETE_SUCCESS));
    }

    @Operation(summary = "회원 추가 (관리자)", description = "관리자 권한으로 이메일 인증 없이 새로운 회원 계정을 생성합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add/member")
    public ResponseEntity<SuccessResponse<Void>> addMember(@RequestBody @Valid UserDto.SignUp newUser) {
        userService.adminSignUp(newUser);
        log.info("관리자에 의해 새로운 회원 추가 완료: {}", newUser.getLoginId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.of(ResponseMessage.SIGNUP_SUCCESS));
    }

    @Operation(summary = "전체 사용자 조회", description = "관리자 권한으로 전체 회원 목록을 반환합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/member")
    public ResponseEntity<List<UserDto.Response>> getAllMembers() {
        List<UserEntity> users = userService.getAllUsers();
        List<UserDto.Response> responseList = users.stream()
                .map(user -> new UserDto.Response(
                        user.getUserId(), user.getEmail(), user.getLoginId(), user.getRole().name(),
                        user.getTotalPoint(), user.getUniv(), user.getCreatedAt(), user.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseList);
    }

    @Operation(summary = "회원 정보 조회", description = "관리자 권한으로 특정 회원의 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/member/{userId}")
    public ResponseEntity<UserDto.Response> getMember(@PathVariable Long userId) {
        UserEntity user = userService.getUserById(userId);
        UserDto.Response responseDto = new UserDto.Response(
                user.getUserId(),
                user.getEmail(),
                user.getLoginId(),
                user.getRole().name(),
                user.getTotalPoint(),
                user.getUniv(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        return ResponseEntity.ok(responseDto);
    }

    // -------------------------------
    // Admin 검증
    // -------------------------------

    @Operation(summary = "관리자 권한 검증", description = "현재 사용자가 관리자임을 확인합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/validate")
    public ResponseEntity<String> validateAdmin() {
        return ResponseEntity.ok("admin");
    }

    // -------------------------------
    // 팀 관리
    // -------------------------------

    @Operation(summary = "모든 팀 반환", description = "관리자 권한으로 모든 팀의 요약 정보를 반환합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/team/all")
    public ResponseEntity<SuccessResponse<List<TeamSummaryDto>>> getAllTeams() {
        List<TeamSummaryDto> teams = teamService.getAllTeams();
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.GET_ALL_TEAMS_SUCCESS, teams));
    }

    @Operation(summary = "팀 생성", description = "관리자 권한으로 팀을 생성합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/team/create")
    public ResponseEntity<SuccessResponse<Void>> createTeam(@RequestParam String teamName) {
        teamService.createTeam(teamName);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.CREATE_TEAM_SUCCESS));
    }

    @Operation(summary = "팀원 추가", description = "관리자 권한으로 팀원(이메일)을 추가합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/team/member/{teamName}")
    public ResponseEntity<SuccessResponse<Void>> addMember(
            @PathVariable String teamName,
            @RequestParam String email
    ) {
        teamService.addMember(teamName, email);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.ADD_TEAM_MEMBER_SUCCESS));
    }

    @Operation(summary = "팀원 삭제", description = "관리자 권한으로 팀원(이메일)을 삭제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/team/member/{teamName}")
    public ResponseEntity<SuccessResponse<Void>> deleteMember(
            @PathVariable String teamName,
            @RequestParam String email
    ) {
        teamService.deleteMember(teamName, email);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.DELETE_TEAM_MEMBER_SUCCESS));
    }

    @Operation(summary = "팀 마일리지 부여", description = "관리자 권한으로 특정 팀에 마일리지를 부여합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/team/mileage/{teamName}")
    public ResponseEntity<SuccessResponse<Void>> grantMileageToTeam(
            @PathVariable String teamName,
            @RequestBody GrantMileageDto grantMileageDto
    ) {
        teamService.grantMileageToTeam(teamName, grantMileageDto.getMileage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.GRANT_MILEAGE_SUCCESS));
    }

    // -------------------------------
    // 결제 관리
    // -------------------------------

    @Operation(summary = "모든 결제 히스토리 조회", description = "관리자 권한으로 모든 팀의 결제 히스토리를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payment/history")
    public ResponseEntity<SuccessResponse<List<TeamPaymentHistoryDto>>> getAllPaymentHistory() {
        List<TeamPaymentHistoryDto> history = paymentService.getAllPaymentHistory();
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.GET_ALL_PAYMENT_HISTORY_SUCCESS, history));
    }

    @Operation(summary = "결제 철회", description = "관리자 권한으로 결제를 철회하고 마일리지를 환불합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/payment/refund/{paymentHistoryId}")
    public ResponseEntity<SuccessResponse<Void>> refundPayment(
            @PathVariable Long paymentHistoryId
    ) {
        paymentService.refundPayment(paymentHistoryId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of(ResponseMessage.REFUND_PAYMENT_SUCCESS));
    }

    // -------------------------------
    // 점수 재계산
    // -------------------------------

    @Operation(summary = "점수 재계산", description = "관리자 권한으로 모든 팀의 점수를 재계산합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/recalculate-points")
    public ResponseEntity<String> recalculatePoints() {
        try {
            log.info("Manual points recalculation started by admin");
            challengeService.updateAllTeamTotalPoints();
            log.info("Manual points recalculation completed");
            return ResponseEntity.ok("점수 재계산 완료");
        } catch (Exception e) {
            log.error("점수 재계산 중 오류 발생: ", e);
            return ResponseEntity.status(500).body("점수 재계산 실패: " + e.getMessage());
        }
    }

    // -------------------------------
    // 대회 시간 설정
    // -------------------------------

    @Operation(summary = "대회 시간 설정", description = "관리자 권한으로 대회 시작/종료 시간을 설정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/contest-time")
    public ResponseEntity<SuccessResponse<ContestConfigDto>> updateContestTime(
            @RequestBody @Valid ContestConfigDto.Request request
    ) {
        ContestConfigDto updatedConfig = contestConfigService.updateContestTime(request);
        log.info("대회 시간 설정 완료: 시작={}, 종료={}", request.getStartTime(), request.getEndTime());
        return ResponseEntity.ok(SuccessResponse.of(
                ResponseMessage.UPDATE_CONTEST_TIME_SUCCESS, updatedConfig
        ));
    }

    // -------------------------------
    // IP 밴 관리
    // -------------------------------

    @Operation(summary = "IP 차단", description = "관리자 권한으로 특정 IP를 차단합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ip-ban")
    public ResponseEntity<SuccessResponse<IPBanDto.Response>> banIP(
            @RequestBody @Valid IPBanDto.BanRequest request,
            HttpServletRequest httpRequest
    ) {
        // 현재 요청자의 정보 가져오기
        String adminLoginId = (String) httpRequest.getAttribute("loginId");
        Long adminId = (Long) httpRequest.getAttribute("userId");

        IPBanEntity banned = ipBanService.banIP(
                request.getIpAddress(),
                request.getReason(),
                request.getBanType(),
                request.getDurationMinutes(),
                adminId,
                adminLoginId
        );

        log.info("IP banned by admin: {} | IP: {} | Reason: {}",
                 adminLoginId, request.getIpAddress(), request.getReason());

        return ResponseEntity.ok(SuccessResponse.of(
                ResponseMessage.IP_BAN_SUCCESS,
                IPBanDto.Response.from(banned)
        ));
    }

    @Operation(summary = "IP 차단 해제", description = "관리자 권한으로 IP 차단을 해제합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/ip-ban/{ipAddress}")
    public ResponseEntity<SuccessResponse<Void>> unbanIP(
            @PathVariable String ipAddress,
            HttpServletRequest httpRequest
    ) {
        String adminLoginId = (String) httpRequest.getAttribute("loginId");

        ipBanService.unbanIP(ipAddress);

        log.info("IP unbanned by admin: {} | IP: {}", adminLoginId, ipAddress);

        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_UNBAN_SUCCESS));
    }

    @Operation(summary = "차단된 IP 목록 조회", description = "관리자 권한으로 현재 차단된 모든 IP 목록을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-ban")
    public ResponseEntity<SuccessResponse<List<IPBanDto.Response>>> getAllBannedIPs() {
        List<IPBanEntity> bannedIPs = ipBanService.getAllActiveBans();

        List<IPBanDto.Response> response = bannedIPs.stream()
                .map(IPBanDto.Response::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(SuccessResponse.of(
                ResponseMessage.GET_BANNED_IPS_SUCCESS,
                response
        ));
    }

    @Operation(summary = "IP 차단 정보 조회", description = "관리자 권한으로 특정 IP의 차단 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-ban/{ipAddress}")
    public ResponseEntity<SuccessResponse<IPBanDto.Response>> getBanInfo(@PathVariable String ipAddress) {
        return ipBanService.getBanInfo(ipAddress)
                .map(ban -> ResponseEntity.ok(SuccessResponse.of(
                        ResponseMessage.GET_BAN_INFO_SUCCESS,
                        IPBanDto.Response.from(ban)
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "IP 차단 연장", description = "관리자 권한으로 임시 차단의 기간을 연장합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/ip-ban/{ipAddress}/extend")
    public ResponseEntity<SuccessResponse<Void>> extendBan(
            @PathVariable String ipAddress,
            @RequestBody @Valid IPBanDto.ExtendRequest request,
            HttpServletRequest httpRequest
    ) {
        String adminLoginId = (String) httpRequest.getAttribute("loginId");

        ipBanService.extendBan(ipAddress, request.getAdditionalMinutes());

        log.info("IP ban extended by admin: {} | IP: {} | Additional minutes: {}",
                 adminLoginId, ipAddress, request.getAdditionalMinutes());

        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_BAN_EXTEND_SUCCESS));
    }

    @Operation(summary = "현재 요청자 IP 조회", description = "관리자가 자신의 IP 주소를 확인합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/my-ip")
    public ResponseEntity<String> getMyIP(HttpServletRequest request) {
        String clientIP = IPAddressUtil.getClientIP(request);
        return ResponseEntity.ok(clientIP);
    }

    @Operation(summary = "IP 밴 캐시 재구축", description = "관리자 권한으로 Redis 캐시를 재구축합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ip-ban/rebuild-cache")
    public ResponseEntity<SuccessResponse<Void>> rebuildCache() {
        ipBanService.rebuildCache();
        log.info("IP ban cache rebuilt by admin");
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.CACHE_REBUILD_SUCCESS));
    }

    @Operation(summary = "IP 활동 로그 조회", description = "관리자 권한으로 IP 활동 로그를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-activity")
    public ResponseEntity<SuccessResponse<List<com.mjsec.ctf.dto.IPActivityDto.Response>>> getIPActivities(
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) Boolean isSuspicious,
            @RequestParam(required = false, defaultValue = "24") Integer hoursBack,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        List<com.mjsec.ctf.domain.IPActivityEntity> activities = ipBanService.getIPActivities(
                ipAddress, activityType, isSuspicious, hoursBack, limit
        );

        List<com.mjsec.ctf.dto.IPActivityDto.Response> response = activities.stream()
                .map(com.mjsec.ctf.dto.IPActivityDto.Response::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_ACTIVITY_LOG_SUCCESS, response));
    }

    @Operation(summary = "의심스러운 IP 목록 조회", description = "관리자 권한으로 의심 활동이 많은 IP 목록을 집계하여 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-suspicious")
    public ResponseEntity<SuccessResponse<List<com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary>>> getSuspiciousIPs(
            @RequestParam(required = false, defaultValue = "24") Integer hoursBack
    ) {
        List<com.mjsec.ctf.dto.IPActivityDto.SuspiciousIPSummary> suspiciousIPs = ipBanService.getSuspiciousIPsSummary(hoursBack);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_SUSPICIOUS_LIST_SUCCESS, suspiciousIPs));
    }

    // -------------------------------
    // IP 화이트리스트 관리
    // -------------------------------

    @Operation(summary = "IP 화이트리스트 추가", description = "관리자 권한으로 IP를 화이트리스트에 추가합니다. 화이트리스트에 추가된 IP는 자동 차단되지 않습니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ip-whitelist")
    public ResponseEntity<SuccessResponse<com.mjsec.ctf.entity.IPWhitelistEntity>> addToWhitelist(
            @RequestBody @Valid com.mjsec.ctf.dto.IPWhitelistDto.AddRequest request,
            HttpServletRequest httpRequest
    ) {
        String adminLoginId = (String) httpRequest.getAttribute("loginId");
        Long adminId = (Long) httpRequest.getAttribute("userId");

        com.mjsec.ctf.entity.IPWhitelistEntity whitelist = ipWhitelistService.addToWhitelist(
                request.getIpAddress(),
                request.getReason(),
                adminId,
                adminLoginId
        );

        log.info("IP added to whitelist by admin: {} | IP: {} | Reason: {}",
                adminLoginId, request.getIpAddress(), request.getReason());

        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_WHITELIST_ADD_SUCCESS, whitelist));
    }

    @Operation(summary = "IP 화이트리스트 제거", description = "관리자 권한으로 IP를 화이트리스트에서 제거합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/ip-whitelist/{ipAddress}")
    public ResponseEntity<SuccessResponse<Void>> removeFromWhitelist(
            @PathVariable String ipAddress,
            HttpServletRequest httpRequest
    ) {
        String adminLoginId = (String) httpRequest.getAttribute("loginId");

        boolean removed = ipWhitelistService.removeFromWhitelist(ipAddress);

        if (removed) {
            log.info("IP removed from whitelist by admin: {} | IP: {}", adminLoginId, ipAddress);
            return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_WHITELIST_REMOVE_SUCCESS));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "활성 화이트리스트 목록 조회", description = "관리자 권한으로 활성 화이트리스트 목록을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-whitelist")
    public ResponseEntity<SuccessResponse<List<com.mjsec.ctf.entity.IPWhitelistEntity>>> getActiveWhitelist() {
        List<com.mjsec.ctf.entity.IPWhitelistEntity> whitelist = ipWhitelistService.getActiveWhitelist();
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_WHITELIST_LIST_SUCCESS, whitelist));
    }

    @Operation(summary = "전체 화이트리스트 목록 조회", description = "관리자 권한으로 전체 화이트리스트 목록을 조회합니다 (비활성 포함).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-whitelist/all")
    public ResponseEntity<SuccessResponse<List<com.mjsec.ctf.entity.IPWhitelistEntity>>> getAllWhitelist() {
        List<com.mjsec.ctf.entity.IPWhitelistEntity> whitelist = ipWhitelistService.getAllWhitelist();
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.IP_WHITELIST_LIST_SUCCESS, whitelist));
    }

    @Operation(summary = "특정 IP 화이트리스트 정보 조회", description = "관리자 권한으로 특정 IP의 화이트리스트 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ip-whitelist/{ipAddress}")
    public ResponseEntity<SuccessResponse<com.mjsec.ctf.entity.IPWhitelistEntity>> getWhitelistInfo(
            @PathVariable String ipAddress
    ) {
        return ipWhitelistService.getWhitelistInfo(ipAddress)
                .map(whitelist -> ResponseEntity.ok(SuccessResponse.of(
                        ResponseMessage.IP_WHITELIST_INFO_SUCCESS,
                        whitelist
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // 파일명 한글 대응
    private String encodeFilename(String filename) {
        // RFC 5987
        return java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
    }
}
