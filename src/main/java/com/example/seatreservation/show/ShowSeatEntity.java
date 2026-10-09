package com.example.seatreservation.show;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "show_seats", uniqueConstraints = @UniqueConstraint(
		name = "uk_show_seat_label", columnNames = { "show_id", "label" }))
public class ShowSeatEntity {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private ShowEntity show;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SeatStatus status;

	protected ShowSeatEntity() {
	}

	ShowSeatEntity(ShowEntity show, String label) {
		this.id = UUID.randomUUID();
		this.show = show;
		this.label = label;
		this.status = SeatStatus.AVAILABLE;
	}

	public String getLabel() {
		return label;
	}

	public SeatStatus getStatus() {
		return status;
	}
}
