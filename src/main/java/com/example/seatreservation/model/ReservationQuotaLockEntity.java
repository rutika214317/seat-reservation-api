package com.example.seatreservation.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_quota_locks")
public class ReservationQuotaLockEntity {

	@EmbeddedId
	private ReservationQuotaLockId id;

	protected ReservationQuotaLockEntity() {
	}

	public ReservationQuotaLockEntity(ReservationQuotaLockId id) {
		this.id = id;
	}
}
