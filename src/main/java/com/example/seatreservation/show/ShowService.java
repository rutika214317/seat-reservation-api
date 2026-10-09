package com.example.seatreservation.show;

import java.util.HashSet;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShowService {

	private final ShowRepository showRepository;

	public ShowService(ShowRepository showRepository) {
		this.showRepository = showRepository;
	}

	@Transactional
	public ShowResponse create(CreateShowRequest request) {
		if (new HashSet<>(request.seats()).size() != request.seats().size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat labels must be unique");
		}

		ShowEntity show = new ShowEntity(request.name(), request.pricePaise());
		request.seats().forEach(show::addSeat);
		return toResponse(showRepository.save(show));
	}

	@Transactional(readOnly = true)
	public ShowResponse get(UUID id) {
		ShowEntity show = showRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));
		return toResponse(show);
	}

	private ShowResponse toResponse(ShowEntity show) {
		var seats = show.getSeats().stream()
				.map(seat -> new ShowResponse.SeatResponse(
						seat.getLabel(), seat.getStatus().name().toLowerCase()))
				.toList();
		long available = seats.stream().filter(seat -> seat.status().equals("available")).count();
		long held = seats.stream().filter(seat -> seat.status().equals("held")).count();
		long confirmed = seats.stream().filter(seat -> seat.status().equals("confirmed")).count();

		return new ShowResponse(
				show.getId(),
				show.getName(),
				show.getPricePaise(),
				seats.size(),
				available,
				held,
				confirmed,
				seats);
	}
}
