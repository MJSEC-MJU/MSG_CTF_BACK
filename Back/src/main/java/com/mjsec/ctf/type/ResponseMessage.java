package com.mjsec.ctf.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResponseMessage {
    OK("성공"),

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

    SUBMIT_SUCCESS("정답"),
    SUBMIT_FAILED_WRONG("오답"),
    SUBMIT_FAILED_WAIT("브루트포스 금지"),
    ALREADY_SUBMITTED("이미 정답처리 된 문제"),
    GET_HISTORY_SUCCESS("히스토리 조회 성공"),

    GENERATE_QR_TOKEN_SUCCESS("QR 발급용 토큰 생성 성공"),
    MILEAGE_BASED_CHECKOUT_SUCCESS("마일리지 기반 결제 성공"),
    GET_PAYMENT_HISTORY_SUCCESS("결제 히스토리 조회 성공"),
    GET_ALL_PAYMENT_HISTORY_SUCCESS("모든 결제 히스토리 조회 성공"),
    REFUND_PAYMENT_SUCCESS("결제 철회 성공"),

    CREATE_TEAM_SUCCESS("팀 생성 성공"),
    ADD_TEAM_MEMBER_SUCCESS("팀원 추가 성공"),
    DELETE_TEAM_MEMBER_SUCCESS("팀원 삭제 성공"),
    GET_TEAM_PROFILE_SUCCESS("팀 프로필 조회 성공"),
    GET_ALL_TEAMS_SUCCESS("모든 팀 조회 성공"),
    GRANT_MILEAGE_SUCCESS("마일리지 부여 성공"),

    SIGNATURE_INSERT_SUCCESS("시그니처가 등록되었습니다."),
    SIGNATURE_CHECK_SUCCESS("시그니처 코드 확인"),
    SIGNATURE_CODES_UPSERT_SUCCESS("시그니처 코드 업서트 성공"),
    SIGNATURE_CODES_IMPORT_SUCCESS("시그니처 코드 임포트 성공"),

    UPDATE_CONTEST_TIME_SUCCESS("대회 시간 설정 성공"),
    GET_CONTEST_TIME_SUCCESS("대회 시간 조회 성공");

    private final String message;
}
