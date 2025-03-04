package com.mjsec.ctf.type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다."),

    // 아이디 관련 에러
    DUPLICATE_ID(HttpStatus.BAD_REQUEST, "사용할 수 없는 아이디입니다."),  // 409 Conflict
    INVALID_ID_LENGTH_MIN(HttpStatus.BAD_REQUEST, "로그인 아이디는 최소 4자 이상이어야 합니다."),
    INVALID_ID_LENGTH_MAX(HttpStatus.BAD_REQUEST, "로그인 아이디는 20자 이하로 입력해주세요."),
    INVALID_ID_CHARACTERS(HttpStatus.BAD_REQUEST, "로그인 아이디는 영문과 숫자만 사용할 수 있습니다."),
    INVALID_ID_WHITESPACE(HttpStatus.BAD_REQUEST, "로그인 아이디에 공백을 포함할 수 없습니다."),
    EMPTY_LOGIN_ID(HttpStatus.BAD_REQUEST, "로그인 아이디를 입력해주세요."),
    INVALID_LOGIN_ID(HttpStatus.UNAUTHORIZED, "아이디를 잘못 입력하셨습니다. 다시 입력해주세요."),

    // 이메일 관련 에러
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."), // 409 Conflict
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식에 맞지 않습니다."), // 400 Bad Request
    UNAUTHORIZED_EMAIL(HttpStatus.BAD_REQUEST, "정해진 이메일 도메인이 아닙니다."),
    EMAIL_VERIFICATION_PENDING(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료되지 않았습니다. 인증을 진행해주세요."),
    FAILED_VERIFICATION(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다."),
    AUTH_ATTEMPT_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 횟수를 초과했습니다. 다시 시도해주세요."),
    EMPTY_EMAIL(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요."),

    // 비밀번호 관련 에러
    EMPTY_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호를 입력해주세요."),
    INVALID_PASSWORD_LENGTH_MIN(HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자 이상이어야 합니다."),
    INVALID_PASSWORD_LENGTH_MAX(HttpStatus.BAD_REQUEST, "비밀번호는 32자 이하로 입력해주세요."),
    INVALID_PASSWORD_WHITESPACE(HttpStatus.BAD_REQUEST, "비밀번호에 공백을 포함할 수 없습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 소문자, 대문자, 숫자 및 특수문자(!@#$%^&*)를 모두 포함해야 합니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호를 잘못 입력하셨습니다. 다시 입력해주세요."),

    // 학교명
    EMPTY_UNIV(HttpStatus.BAD_REQUEST, "학교명을 입력해주세요."),

    //문제 관련
    REQUIRED_FIELD_NULL(HttpStatus.BAD_REQUEST, "필수값이 누락되어 있습니다."),
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND,"문제 ID를 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    
    //유저
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 역할입니다.");
    ;

    private final HttpStatus httpStatus;
    private final String description;
}