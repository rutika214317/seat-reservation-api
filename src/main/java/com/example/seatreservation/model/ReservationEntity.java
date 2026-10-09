package com.example.seatreservation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(
		name = "uk_reservation_idempotency",
		columnNames = { "show_id", "user_id", "idempotency_key" }))
public class ReservationEntity {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private ShowEntity show;

	@Column(name = "user_id", nullable = false, length = 100)
	private String userId;

	@Column(name = "idempotency_key", nullable = false, length = 200)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "amount_paise", nullable = false)
	private long amountPaise;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@ElementCollection
	@CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
	@OrderColumn(name = "seat_order")
	@Column(name = "seat_label", nullable = false, length = 50)
	private List<String> seats = new ArrayList<>();

	protected ReservationEntity() {
	}

	public ReservationEntity(UUID id, ShowEntity show, String userId, String idempotencyKey, String requestHash,
			long amountPaise, List<String> seats) {
		this.id = id;
		this.show = show;
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.amountPaise = amountPaise;
		this.seats = new ArrayList<>(seats);
		this.status = ReservationStatus.CONFIRMED;
	}

	public UUID getId() {
		return id;
	}

	public ShowEntity getShow() {
		return show;
	}

	public String getUserId() {
		return userId;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public long getAmountPaise() {
		return amountPaise;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public List<String> getSeats() {
		return seats;
	}

	public void cancel() {
		status = ReservationStatus.CANCELLED;
	}
}
