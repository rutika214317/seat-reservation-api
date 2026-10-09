package com.example.seatreservation.show;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateShowRequest(
		@NotBlank @Size(max = 200) String name,
		@NotEmpty List<@NotBlank @Size(max = 50) String> seats,
		@NotNull @PositiveOrZero @JsonProperty("price_paise") Long pricePaise) {
}
