package com.mjsec.ctf.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseMessage {
    SIGNUP_SUCCESS("회원가입 성공"),
    LOGIN_SUCCESS("로그인 성공"),
    LOGOUT_SUCCESS("로그아웃 성공"),
    PROFILE_SUCCESS("유저 프로필 조회 성공"),
    UPDATE_SUCCESS("회원정보 수정 성공"),
    AUTH_SUCCESS("인증 성공"),
    DELETE_SUCCESS("회원 삭제 성공"),
    CREATE_CHALLENGE_SUCCESS("문제 생성 성공"),
    UPDATE_CHALLENGE_SUCCESS("문제 수정 성공"),
    DELETE_CHALLENGE_SUCCESS("문제 삭제 성공"),
    GET_ALL_CHALLENGE_SUCCESS("모든 문제 조회 성공"),
    GET_CHALLENGE_DETAIL_SUCCESS("특정 문제 상세 정보 조회 성공"),
    ;

    private final String message;
}