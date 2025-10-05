package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.CheckRequest;
import com.mjsec.ctf.dto.CheckResponse;
import com.mjsec.ctf.service.SignatureCheckerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CodeCheckController {

    private final SignatureCheckerService service;

    /**
     * 요청 형식: { "club":"MJSEC", "value":"ABC123" }
     * 응답 형식: { "return":"True" } 또는 { "return":"False" }
     * 대소문자 무시: /api/check-code?ignoreCase=true
     */
    @Operation(summary = "클럽 코드 존재 여부 검사", description = "JSON 코드 목록에서 해당 값 존재 여부를 반환합니다.")
    @PostMapping(
            path = "/check-code",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public CheckResponse check(@Valid @RequestBody CheckRequest req,
                               @RequestParam(defaultValue = "false") boolean ignoreCase) throws Exception {
        boolean exists = service.exists(req.getClub(), req.getValue(), ignoreCase);
        return new CheckResponse(exists ? "True" : "False");
    }
}
