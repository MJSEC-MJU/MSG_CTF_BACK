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
        boolean exists = signatureRepository.existsByNameAndSignatureAndClub(
                request.getName(), request.getSignature(), request.getClub());

        if (exists) {
            return SignatureDto.InsertResponse.builder()
                    .result(false)
                    .id(null)
                    .build();
        }

        SignatureEntity saved = signatureRepository.save(
                SignatureEntity.builder()
                        .name(request.getName())
                        .signature(request.getSignature())
                        .club(request.getClub())
                        .build()
        );

        return SignatureDto.InsertResponse.builder()
                .result(true)
                .id(saved.getSignatureId())
                .build();
    }
}

