package com.example.seatreservation.controller;

import java.net.URI;
import java.util.UUID;

import com.example.seatreservation.dto.CreateShowRequest;
import com.example.seatreservation.dto.ShowResponse;
import com.example.seatreservation.service.ShowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shows")
public class ShowController {

	private final ShowService showService;

	public ShowController(ShowService showService) {
		this.showService = showService;
	}

	@PostMapping
	@Operation(summary = "Create a show", security = @SecurityRequirement(name = "basicAuth"))
	public ResponseEntity<ShowResponse> create(@Valid @RequestBody CreateShowRequest request) {
		ShowResponse show = showService.create(request);
		return ResponseEntity.created(URI.create("/shows/" + show.id())).body(show);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get show seats and availability counts")
	public ShowResponse get(@PathVariable UUID id) {
		return showService.get(id);
	}
}
