package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.SignatureEntity;
import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignatureService {

    private final SignatureRepository signatureRepository;

    public SignatureDto.CheckResponse checkCode(SignatureDto.Request request) {
        boolean ok = signatureRepository.existsByNameAndSignatureAndClub(
                request.getName(), request.getSignature(), request.getClub());

        return SignatureDto.CheckResponse.builder()
                .result(ok)
                .build();
    }

    @Transactional
    public SignatureDto.InsertResponse insertCode(SignatureDto.Request request) {
        // signature 자체가 PK이므로 먼저 이걸로 막기
        if (signatureRepository.existsById(request.getSignature())) {
            return new SignatureDto.InsertResponse(false, null);
        }
        var saved = signatureRepository.save(SignatureEntity.builder()
                .signature(request.getSignature()) // PK 직접 할당
                .name(request.getName())
                .club(request.getClub())
                .build());
        return new SignatureDto.InsertResponse(true, saved.getSignature());
    }
}

