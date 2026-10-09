package com.example.seatreservation.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShowResponse(
		UUID id,
		String name,
		@JsonProperty("price_paise") long pricePaise,
		@JsonProperty("total_seats") int totalSeats,
		@JsonProperty("available_seats") long availableSeats,
		@JsonProperty("held_seats") long heldSeats,
		@JsonProperty("confirmed_seats") long confirmedSeats,
		List<SeatResponse> seats) {

	public record SeatResponse(String label, String status) {
	}
}
