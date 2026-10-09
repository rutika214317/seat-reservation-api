package com.example.seatreservation.repository;

import java.util.Optional;
import java.util.UUID;

import com.example.seatreservation.model.ShowEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShowRepository extends JpaRepository<ShowEntity, UUID> {

	@Override
	@EntityGraph(attributePaths = "seats")
	Optional<ShowEntity> findById(UUID id);

	@Query("select show from ShowEntity show where show.id = :id")
	Optional<ShowEntity> findShowById(UUID id);
}
