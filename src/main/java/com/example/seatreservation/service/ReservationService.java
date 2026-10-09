package com.example.seatreservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import com.example.seatreservation.dto.ReservationResponse;
import com.example.seatreservation.model.ReservationEntity;
import com.example.seatreservation.model.ReservationQuotaLockId;
import com.example.seatreservation.model.ReservationStatus;
import com.example.seatreservation.model.SeatStatus;
import com.example.seatreservation.model.ShowEntity;
import com.example.seatreservation.observability.ReservationMetrics;
import com.example.seatreservation.repository.ReservationQuotaLockRepository;
import com.example.seatreservation.repository.ReservationRepository;
import com.example.seatreservation.repository.ShowRepository;
import com.example.seatreservation.repository.ShowSeatRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

	private static final int PER_USER_LIMIT = 4;

	private final ShowRepository showRepository;
	private final ShowSeatRepository showSeatRepository;
	private final ReservationRepository reservationRepository;
	private final ReservationQuotaLockRepository quotaLockRepository;
	private final EntityManager entityManager;
	private final ReservationMetrics reservationMetrics;

	public ReservationService(
			ShowRepository showRepository,
			ShowSeatRepository showSeatRepository,
			ReservationRepository reservationRepository,
			ReservationQuotaLockRepository quotaLockRepository,
			EntityManager entityManager,
			ReservationMetrics reservationMetrics) {
		this.showRepository = showRepository;
		this.showSeatRepository = showSeatRepository;
		this.reservationRepository = reservationRepository;
		this.quotaLockRepository = quotaLockRepository;
		this.entityManager = entityManager;
		this.reservationMetrics = reservationMetrics;
	}

	@Transactional
	public ReservationResponse reserve(UUID showId, String userId, List<String> requestedSeats, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An idempotency key is required");
		}
		if (idempotencyKey.length() > 200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key must be at most 200 characters");
		}
		if (new HashSet<>(requestedSeats).size() != requestedSeats.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat labels must be unique");
		}

		ShowEntity show = showRepository.findShowById(showId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));

		ReservationQuotaLockId quotaLockId = new ReservationQuotaLockId(showId, userId);
		quotaLockRepository.findByIdForUpdate(quotaLockId)
				.orElseThrow(() -> new IllegalStateException("Reservation quota lock row was not created"));

		String requestHash = requestHash(requestedSeats);
		var existing = reservationRepository.findByShowIdAndUserIdAndIdempotencyKey(
				showId, userId, idempotencyKey);
		if (existing.isPresent()) {
			ReservationEntity reservation = existing.get();
			if (!reservation.getRequestHash().equals(requestHash)) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "Idempotency key was already used for a different request");
			}
			reservationMetrics.recordIdempotentReplay();
			return toResponse(reservation);
		}

		var seats = showSeatRepository.findByShowAndLabelsForUpdate(showId, requestedSeats);
		if (seats.size() != requestedSeats.size()
				|| seats.stream().anyMatch(seat -> seat.getStatus() != SeatStatus.AVAILABLE)) {
			throw decline("seat-taken", "One or more requested seats are unavailable");
		}

		long alreadyReserved = showSeatRepository.countByShowIdAndReservedByAndStatus(
				showId, userId, SeatStatus.CONFIRMED);
		if (alreadyReserved + requestedSeats.size() > PER_USER_LIMIT) {
			throw decline("per-user-limit", "Per-user seat limit exceeded");
		}
		if (show.getPricePaise() > Long.MAX_VALUE / requestedSeats.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation amount exceeds the supported range");
		}

		UUID reservationId = UUID.randomUUID();
		int claimed = showSeatRepository.confirmAvailable(
				showId, requestedSeats, SeatStatus.AVAILABLE, SeatStatus.CONFIRMED, userId, reservationId);
		if (claimed != requestedSeats.size()) {
			throw decline("seat-taken", "One or more requested seats are unavailable");
		}

		ReservationEntity reservation = new ReservationEntity(
				reservationId, show, userId, idempotencyKey, requestHash,
				show.getPricePaise() * requestedSeats.size(), requestedSeats);
		reservationRepository.save(reservation);
		recordConfirmedAfterCommit();
		return toResponse(reservation);
	}

	private ResponseStatusException decline(String reason, String message) {
		reservationMetrics.recordDeclined(reason);
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	private void recordConfirmedAfterCommit() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Reservation transaction synchronization is not active");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				reservationMetrics.recordConfirmed();
			}
		});
	}

	@Transactional
	public ReservationResponse cancel(UUID reservationId, String userId) {
		ReservationEntity current = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
		UUID showId = current.getShow().getId();
		if (!current.getUserId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the reservation owner can cancel it");
		}

		ReservationQuotaLockId quotaLockId = new ReservationQuotaLockId(showId, userId);
		quotaLockRepository.findByIdForUpdate(quotaLockId)
				.orElseThrow(() -> new IllegalStateException("Reservation quota lock row was not created"));

		ReservationEntity reservation = current;
		entityManager.refresh(reservation);
		if (!reservation.getUserId().equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the reservation owner can cancel it");
		}
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {
			return toResponse(reservation);
		}

		var seats = showSeatRepository.findByShowAndLabelsForUpdate(showId, reservation.getSeats());
		if (seats.size() != reservation.getSeats().size()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation seat records are incomplete");
		}
		int released = showSeatRepository.releaseReservation(
				showId, reservationId, userId, SeatStatus.CONFIRMED, SeatStatus.AVAILABLE);
		if (released != reservation.getSeats().size()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation seats are no longer owned by this reservation");
		}
		reservation.cancel();
		return toResponse(reservation);
	}

	private ReservationResponse toResponse(ReservationEntity reservation) {
		return new ReservationResponse(
				reservation.getId(),
				reservation.getShow().getId(),
				reservation.getUserId(),
				List.copyOf(reservation.getSeats()),
				reservation.getAmountPaise(),
				reservation.getStatus().name().toLowerCase());
	}

	private String requestHash(List<String> seats) {
		String canonical = seats.stream().sorted().reduce((left, right) -> left + "\n" + right).orElse("");
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
