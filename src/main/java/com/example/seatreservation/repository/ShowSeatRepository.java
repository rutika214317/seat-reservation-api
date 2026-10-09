package com.example.seatreservation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.example.seatreservation.model.SeatStatus;
import com.example.seatreservation.model.ShowSeatEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeatEntity, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select seat from ShowSeatEntity seat
			where seat.show.id = :showId and seat.label in :labels
			order by seat.label
			""")
	List<ShowSeatEntity> findByShowAndLabelsForUpdate(
			@Param("showId") UUID showId, @Param("labels") Collection<String> labels);

	long countByShowIdAndReservedByAndStatus(UUID showId, String reservedBy, SeatStatus status);

	@Modifying
	@Query("""
			update ShowSeatEntity seat
			set seat.status = :confirmed, seat.reservedBy = :userId, seat.reservationId = :reservationId
			where seat.show.id = :showId and seat.label in :labels and seat.status = :available
			""")
	int confirmAvailable(
			@Param("showId") UUID showId,
			@Param("labels") Collection<String> labels,
			@Param("available") SeatStatus available,
			@Param("confirmed") SeatStatus confirmed,
			@Param("userId") String userId,
			@Param("reservationId") UUID reservationId);

	@Modifying
	@Query("""
			update ShowSeatEntity seat
			set seat.status = :available, seat.reservedBy = null, seat.reservationId = null
			where seat.show.id = :showId and seat.reservationId = :reservationId
				and seat.reservedBy = :userId and seat.status = :confirmed
			""")
	int releaseReservation(
			@Param("showId") UUID showId,
			@Param("reservationId") UUID reservationId,
			@Param("userId") String userId,
			@Param("confirmed") SeatStatus confirmed,
			@Param("available") SeatStatus available);
}
