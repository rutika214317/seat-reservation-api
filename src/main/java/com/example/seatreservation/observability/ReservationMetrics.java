package com.example.seatreservation.observability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.seatreservation.model.SeatStatus;
import com.example.seatreservation.repository.ShowRepository;
import com.example.seatreservation.repository.ShowSeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ReservationMetrics {

	private final MeterRegistry meterRegistry;
	private final ShowRepository showRepository;
	private final ShowSeatRepository showSeatRepository;
	private final Map<UUID, UUID> registeredShows = new ConcurrentHashMap<>();

	public ReservationMetrics(
			MeterRegistry meterRegistry,
			ShowRepository showRepository,
			ShowSeatRepository showSeatRepository) {
		this.meterRegistry = meterRegistry;
		this.showRepository = showRepository;
		this.showSeatRepository = showSeatRepository;
		meterRegistry.counter("seat_reservations_confirmed_total");
		meterRegistry.counter("seat_reservations_idempotent_replays_total");
		counterForReason("seat-taken");
		counterForReason("per-user-limit");
	}

	@EventListener(ApplicationReadyEvent.class)
	public void registerExistingShows() {
		showRepository.findAll().forEach(show -> registerShow(show.getId()));
	}

	public void registerShow(UUID showId) {
		registeredShows.computeIfAbsent(showId, id -> {
			Gauge.builder("seat_reservation_seats_available", id,
							show -> showSeatRepository.countByShowIdAndStatus(show, SeatStatus.AVAILABLE))
					.description("Currently available seats for a show")
					.tag("show_id", id.toString())
					.register(meterRegistry);
			return id;
		});
	}

	public void registerShowAfterCommit(UUID showId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Show transaction synchronization is not active");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				registerShow(showId);
			}
		});
	}

	public void recordConfirmed() {
		meterRegistry.counter("seat_reservations_confirmed_total").increment();
	}

	public void recordDeclined(String reason) {
		counterForReason(reason).increment();
	}

	public void recordIdempotentReplay() {
		meterRegistry.counter("seat_reservations_idempotent_replays_total").increment();
	}

	private Counter counterForReason(String reason) {
		return Counter.builder("seat_reservations_declined_total")
				.tag("reason", reason)
				.register(meterRegistry);
	}
}
