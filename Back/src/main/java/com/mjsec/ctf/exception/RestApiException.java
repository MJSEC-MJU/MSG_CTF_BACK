package com.mjsec.ctf.exception;

import com.mjsec.ctf.type.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RestApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String message;

    public RestApiException(ErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
        this.message = errorCode.getDescription();
    }
}