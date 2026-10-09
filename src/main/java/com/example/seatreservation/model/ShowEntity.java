package com.example.seatreservation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "shows")
public class ShowEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "price_paise", nullable = false)
	private long pricePaise;

	@OneToMany(mappedBy = "show", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<ShowSeatEntity> seats = new ArrayList<>();

	protected ShowEntity() {
	}

	public ShowEntity(String name, long pricePaise) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.pricePaise = pricePaise;
	}

	public void addSeat(String label) {
		seats.add(new ShowSeatEntity(this, label));
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getPricePaise() {
		return pricePaise;
	}

	public List<ShowSeatEntity> getSeats() {
		return seats;
	}
}
