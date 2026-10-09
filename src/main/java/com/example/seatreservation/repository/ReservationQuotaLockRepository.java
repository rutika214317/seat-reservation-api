package com.example.seatreservation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import com.example.seatreservation.model.ReservationQuotaLockEntity;
import com.example.seatreservation.model.ReservationQuotaLockId;

public interface ReservationQuotaLockRepository
		extends JpaRepository<ReservationQuotaLockEntity, ReservationQuotaLockId> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select quotaLock from ReservationQuotaLockEntity quotaLock where quotaLock.id = :id")
	Optional<ReservationQuotaLockEntity> findByIdForUpdate(@Param("id") ReservationQuotaLockId id);
}
