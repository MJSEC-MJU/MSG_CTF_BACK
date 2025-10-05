package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class CheckResponse {
    @JsonProperty("return")
    private String result; // "True" 또는 "False"
}
