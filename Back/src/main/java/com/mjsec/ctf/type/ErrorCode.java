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
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서부 오류가 발생했습니다."),

    DUPLICATE_ID(HttpStatus.BAD_REQUEST, "사용할 수 없는 아이디입니다."),  // 409 Conflict
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."), // 409 Conflict
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "이메일 형식에 맞지 않습니다."), // 400 Bad Request

    INVALID_LOGIN_ID(HttpStatus.UNAUTHORIZED, "아이디를 잘못 입력하셨습니다. 다시 입력해주세요."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호를 잘못 입력하셨습니다. 다시 입력해주세요."),

    ;

    private final HttpStatus httpStatus;
    private final String description;
}