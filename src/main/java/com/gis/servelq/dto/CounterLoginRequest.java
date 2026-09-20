package com.gis.servelq.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CounterLoginRequest {
    @NotBlank
    private String counterId;
    /**
     * Feedback app: attach to the counter the agent already holds, never claim/switch.
     */
    private boolean attachOnly = false;
}