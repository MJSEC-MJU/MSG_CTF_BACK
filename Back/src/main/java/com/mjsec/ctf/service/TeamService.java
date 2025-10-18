package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.TeamHistoryEntity;
import com.mjsec.ctf.domain.TeamPaymentHistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.TeamHistoryDto;
import com.mjsec.ctf.dto.TeamProfileDto;
import com.mjsec.ctf.dto.TeamSummaryDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.TeamHistoryRepository;
import com.mjsec.ctf.repository.TeamPaymentHistoryRepository;
import com.mjsec.ctf.repository.TeamRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamPaymentHistoryRepository teamPaymentHistoryRepository;
    private final TeamHistoryRepository teamHistoryRepository;
    private final ChallengeRepository challengeRepository;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
                       TeamPaymentHistoryRepository teamPaymentHistoryRepository,
                       TeamHistoryRepository teamHistoryRepository,
                       ChallengeRepository challengeRepository) {

        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamPaymentHistoryRepository = teamPaymentHistoryRepository;
        this.teamHistoryRepository = teamHistoryRepository;
        this.challengeRepository = challengeRepository;
    }

    public void createTeam(String teamName) {

        if (teamRepository.existsByTeamName(teamName)) {
            throw new RestApiException(ErrorCode.TEAM_ALREADY_EXIST);
        }

        TeamEntity team = TeamEntity.builder()
                .teamName(teamName)
                .mileage(0)
                .totalPoint(0)
                .build();

        teamRepository.save(team);
    }

    public void saveTeam(TeamEntity team) {
        teamRepository.save(team);
    }   //메서드 추가 (10/6)


    @Transactional
    public void addMember(String teamName, String email) {

        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.hasTeam()) {
            throw new RestApiException(ErrorCode.ALREADY_HAVE_TEAM);
        }

        if(!team.getMemberUserIds().isEmpty() && team.getMemberUserIds().size() == 2) {
            throw new RestApiException(ErrorCode.TEAM_FULL);
        }

        team.addMember(user.getUserId());
        teamRepository.save(team);

        user.joinTeam(team.getTeamId());
        userRepository.save(user);
    }

    @Transactional
    public void deleteMember(String teamName, String email) {

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if(!team.isMember(user.getUserId())) {
            throw new RestApiException(ErrorCode.TEAM_MISMATCH);
        }

        team.removeMember(user.getUserId());
        user.leaveTeam();

        teamRepository.save(team);
        userRepository.save(user);
    }

    public boolean canSolveChallenge(Long userId, Long challengeId) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        return !team.hasSolvedChallenge(challengeId);
    }

    @Transactional
    public void recordTeamSolution(Long userId, Long challengeId, int points, int mileage) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        team.addSolvedChallenge(challengeId, points, mileage);
        teamRepository.save(team);
    }

    public boolean useTeamMileage(Long teamId, int amount, Long requesterUserId) {

        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if (team.deductMileage(amount)) {
            teamRepository.save(team);

            TeamPaymentHistoryEntity paymentHistory = TeamPaymentHistoryEntity.builder()
                    .teamId(teamId)
                    .requesterUserId(requesterUserId)
                    .mileageUsed(amount)
                    .createdAt(LocalDateTime.now())
                    .build();

            teamPaymentHistoryRepository.save(paymentHistory);
            return true;
        }
        return false;
    }

    public Optional<TeamEntity> getUserTeam(Long teamId) {

        return teamRepository.findById(teamId);
    }

    public List<TeamEntity> getTeamRanking() {

        return teamRepository.findAllByOrderByTotalPointDescLastSolvedTimeAsc();
    }

    public TeamProfileDto getTeamProfile(String loginId) {

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = getUserTeam(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        List<String> memberEmails = new ArrayList<>();
        if (!team.getMemberUserIds().isEmpty()) {
            memberEmails = userRepository.findAllById(team.getMemberUserIds()).stream()
                    .map(UserEntity::getEmail)
                    .collect(Collectors.toList());
        }

        return TeamProfileDto.builder()
                .teamId(team.getTeamId())
                .teamName(team.getTeamName())
                .userEmail(user.getEmail())
                .memberEmail(memberEmails)
                .teamMileage(team.getMileage())
                .teamTotalPoint(team.getTotalPoint())
                .teamSolvedCount(team.getSolvedCount())
                .build();
    }

    public List<Long> getTeamSolvedChallenges(Long teamId) {

        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        return team.getSolvedChallengeIds();
    }

    public List<String> getTeamMemberLoginIds(List<Long> memberUserIds) {
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userRepository.findAllById(memberUserIds).stream()
                .map(UserEntity::getLoginId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<TeamSummaryDto> getAllTeams() {

        List<TeamEntity> teams = teamRepository.findAll();

        return teams.stream()
                .map(team -> {
                    List<String> memberEmails = new ArrayList<>();
                    if (team.getMemberUserIds() != null && !team.getMemberUserIds().isEmpty()) {
                        memberEmails = userRepository.findAllById(team.getMemberUserIds()).stream()
                                .map(UserEntity::getEmail)
                                .collect(Collectors.toList());
                    }

                    return TeamSummaryDto.builder()
                            .teamId(team.getTeamId())
                            .teamName(team.getTeamName())
                            .teamTotalPoint(team.getTotalPoint())
                            .teamMileage(team.getMileage())
                            .memberEmails(memberEmails)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // 관리자가 팀에 마일리지 부여
    @Transactional
    public void grantMileageToTeam(String teamName, int mileage) {
        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if (mileage < 0) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "마일리지는 0 이상이어야 합니다.");
        }

        team.addMileage(mileage);
        teamRepository.save(team);

        log.info("Admin granted mileage: teamId={}, teamName={}, mileageGranted={}",
                team.getTeamId(), teamName, mileage);
    }

    // 팀 히스토리 조회 (자기 팀의 풀이 기록)
    public List<TeamHistoryDto> getTeamHistory(String loginId) {
        log.info("팀 히스토리 조회 시작: loginId={}", loginId);

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        List<TeamHistoryEntity> histories = teamHistoryRepository.findByTeamNameOrderBySolvedTimeAsc(team.getTeamName());

        List<TeamHistoryDto> historyDtos = histories.stream()
                .map(history -> {
                    Optional<ChallengeEntity> challengeOpt = challengeRepository.findById(history.getChallengeId());
                    if (!challengeOpt.isPresent()) {
                        return null;
                    }

                    ChallengeEntity challenge = challengeOpt.get();
                    return new TeamHistoryDto(
                            team.getTeamId(),
                            team.getTeamName(),
                            String.valueOf(challenge.getChallengeId()),
                            challenge.getTitle(),
                            history.getSolvedTime(),
                            challenge.getPoints()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("팀 히스토리 조회 완료: loginId={}, teamName={}, recordCount={}",
                loginId, team.getTeamName(), historyDtos.size());

        return historyDtos;
    }
}
