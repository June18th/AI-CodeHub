package com.aicodehub.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequest {
    @NotNull
    private Long userId;
    @NotBlank
    private String action;  // approve / reject
    private String role;    // user / beta (approve 时指定)
}
