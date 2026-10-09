package com.example.seatreservation.show;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowRepository extends JpaRepository<ShowEntity, UUID> {

	@Override
	@EntityGraph(attributePaths = "seats")
	Optional<ShowEntity> findById(UUID id);
}
