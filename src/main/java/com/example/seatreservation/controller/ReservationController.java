package com.example.seatreservation.controller;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

import com.example.seatreservation.dto.ReservationRequest;
import com.example.seatreservation.dto.ReservationResponse;
import com.example.seatreservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping("/shows/{id}/reserve")
	@Operation(summary = "Reserve seats for the authenticated user", security = @SecurityRequirement(name = "basicAuth"))
	public ResponseEntity<ReservationResponse> reserve(
			@PathVariable UUID id,
			@Valid @RequestBody ReservationRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String headerKey,
			Principal principal) {
		if (headerKey != null && request.idempotencyKey() != null
				&& !headerKey.equals(request.idempotencyKey())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key header and body must match");
		}
		String key = headerKey != null ? headerKey : request.idempotencyKey();
		ReservationResponse response = reservationService.reserve(id, principal.getName(), request.seats(), key);
		return ResponseEntity.created(URI.create("/reservations/" + response.reservationId())).body(response);
	}

	@PostMapping("/reservations/{id}/cancel")
	@Operation(summary = "Cancel a reservation owned by the authenticated user",
			security = @SecurityRequirement(name = "basicAuth"))
	public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id, Principal principal) {
		ReservationResponse response = reservationService.cancel(id, principal.getName());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(response);
	}
}
