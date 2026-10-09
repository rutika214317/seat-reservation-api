package com.example.seatreservation.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ReservationRequest(
		@NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 50) String> seats,
		@Size(max = 200) @JsonProperty("idempotency_key") String idempotencyKey) {
}
