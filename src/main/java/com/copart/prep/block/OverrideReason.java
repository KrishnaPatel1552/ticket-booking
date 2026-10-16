package com.copart.prep.block;

import jakarta.validation.constraints.NotBlank;

public record OverrideReason(@NotBlank String reason) {
}
