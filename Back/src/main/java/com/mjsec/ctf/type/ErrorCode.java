package com.mjsec.ctf.type;

import lombok.Getter;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다."),

    DUPLICATE_ID(HttpStatus.BAD_REQUEST, "사용할 수 없는 아이디입니다."),
    INVALID_ID_LENGTH_MIN(HttpStatus.BAD_REQUEST, "로그인 아이디는 최소 4자 이상이어야 합니다."),
    INVALID_ID_LENGTH_MAX(HttpStatus.BAD_REQUEST, "로그인 아이디는 20자 이하로 입력해주세요."),
    INVALID_ID_CHARACTERS(HttpStatus.BAD_REQUEST, "로그인 아이디는 영문과 숫자만 사용할 수 있습니다."),
    INVALID_ID_WHITESPACE(HttpStatus.BAD_REQUEST, "로그인 아이디에 공백을 포함할 수 없습니다."),
    EMPTY_LOGIN_ID(HttpStatus.BAD_REQUEST, "로그인 아이디를 입력해주세요."),
    INVALID_LOGIN_ID(HttpStatus.UNAUTHORIZED, "아이디를 잘못 입력하셨습니다. 다시 입력해주세요."),

    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식에 맞지 않습니다."),
    UNAUTHORIZED_EMAIL(HttpStatus.BAD_REQUEST, "정해진 이메일 도메인이 아닙니다."),
    EMAIL_VERIFICATION_PENDING(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료되지 않았습니다. 인증을 진행해주세요."),
    FAILED_VERIFICATION(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다."),
    AUTH_ATTEMPT_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 횟수를 초과했습니다. 다시 시도해주세요."),
    EMPTY_EMAIL(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요."),

    EMPTY_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호를 입력해주세요."),
    INVALID_PASSWORD_LENGTH_MIN(HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자 이상이어야 합니다."),
    INVALID_PASSWORD_LENGTH_MAX(HttpStatus.BAD_REQUEST, "비밀번호는 32자 이하로 입력해주세요."),
    INVALID_PASSWORD_WHITESPACE(HttpStatus.BAD_REQUEST, "비밀번호에 공백을 포함할 수 없습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 소문자, 대문자, 숫자 및 특수문자(!@#$%^&*)를 모두 포함해야 합니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호를 잘못 입력하셨습니다. 다시 입력해주세요."),

    EMPTY_UNIV(HttpStatus.BAD_REQUEST, "학교명을 입력해주세요."),

    REQUIRED_FIELD_NULL(HttpStatus.BAD_REQUEST, "필수값이 누락되어 있습니다."),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 ID를 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다."),
    LEADERBOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "유저에 대한 리더보드를 찾을 수 없습니다."),
    EARLY_EXIT_USER(HttpStatus.FORBIDDEN, "조기 퇴소자는 문제를 제출할 수 없습니다."),

    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 토큰입니다."),
    NOT_ENOUGH_MILEAGE(HttpStatus.BAD_REQUEST, "마일리지가 부족합니다."),
    PAYMENT_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 히스토리를 찾을 수 없습니다."),

    TEAM_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 팀을 찾을 수 없습니다."),
    MUST_BE_BELONG_TEAM(HttpStatus.BAD_REQUEST, "팀에 소속되지 않은 사용자 입니다."),
    ALREADY_HAVE_TEAM(HttpStatus.BAD_REQUEST, "이미 팀에 소속된 사용자 입니다."),
    TEAM_ALREADY_EXIST(HttpStatus.BAD_REQUEST, "사용 중인 팀 이름 입니다."),
    TEAM_MISMATCH(HttpStatus.BAD_REQUEST, "해당 팀의 팀원이 아닙니다."),
    TEAM_FULL(HttpStatus.BAD_REQUEST, "팀원 정원 초과입니다."),

    SIGNATURE_DUPLICATE(HttpStatus.BAD_REQUEST, "중복된 시그니처 코드입니다."),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "잘못된 시그니처 코드입니다."),

    CONTEST_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "대회 설정을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String description;
}
