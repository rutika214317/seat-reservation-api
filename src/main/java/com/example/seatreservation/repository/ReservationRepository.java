package com.example.seatreservation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.seatreservation.model.ReservationEntity;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

	Optional<ReservationEntity> findByShowIdAndUserIdAndIdempotencyKey(
			UUID showId, String userId, String idempotencyKey);
}
