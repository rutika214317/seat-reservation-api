package com.example.seatreservation.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ReservationQuotaLockId implements Serializable {

	@Column(name = "show_id", nullable = false)
	private UUID showId;

	@Column(name = "user_id", nullable = false, length = 100)
	private String userId;

	protected ReservationQuotaLockId() {
	}

	public ReservationQuotaLockId(UUID showId, String userId) {
		this.showId = showId;
		this.userId = userId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ReservationQuotaLockId that)) {
			return false;
		}
		return Objects.equals(showId, that.showId) && Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(showId, userId);
	}
}
