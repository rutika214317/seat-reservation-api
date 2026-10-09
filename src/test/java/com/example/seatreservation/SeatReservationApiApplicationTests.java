package com.example.seatreservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"app.security.basic.username=admin",
		"app.security.basic.password=change-this-local-password",
		"app.security.reservation-users=alice:alice-password,bob:bob-password"
})
@AutoConfigureMockMvc
class SeatReservationApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createsShowAndReturnsAvailableSeatState() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/shows")
						.with(httpBasic("admin", "change-this-local-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"friday-night","seats":["A1","A2"],"price_paise":25000}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("friday-night"))
				.andExpect(jsonPath("$.price_paise").value(25000))
				.andExpect(jsonPath("$.total_seats").value(2))
				.andExpect(jsonPath("$.available_seats").value(2))
				.andExpect(jsonPath("$.held_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(0))
				.andExpect(jsonPath("$.seats[0].status").value("available"))
				.andReturn();

		String showId = com.jayway.jsonpath.JsonPath.read(
				createResult.getResponse().getContentAsString(), "$.id");
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available_seats").value(2))
				.andExpect(jsonPath("$.seats.length()").value(2));
	}

	@Test
	void showCreationRequiresAdminAuthorization() throws Exception {
		mockMvc.perform(post("/shows")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"friday-night","seats":["A1"],"price_paise":25000}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void showCreationRejectsInvalidPassword() throws Exception {
		mockMvc.perform(post("/shows")
						.with(httpBasic("admin", "incorrect-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"friday-night","seats":["A1"],"price_paise":25000}
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reservationPostRoutesRequireAuthentication() throws Exception {
		mockMvc.perform(post("/shows/00000000-0000-0000-0000-000000000001/reserve"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reservationIsIdempotentAndRejectsChangedPayloadForSameKey() throws Exception {
		String showId = createShow("A1", "A2");
		String request = """
				{"seats":["A1"],"idempotency_key":"order-1"}
				""";
		MvcResult original = mockMvc.perform(post("/shows/{id}/reserve", showId)
						.with(httpBasic("alice", "alice-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").value("alice"))
				.andExpect(jsonPath("$.amount_paise").value(25000))
				.andReturn();

		MvcResult replay = mockMvc.perform(post("/shows/{id}/reserve", showId)
						.with(httpBasic("alice", "alice-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isCreated())
				.andReturn();
		String originalReservationId = com.jayway.jsonpath.JsonPath.read(
				original.getResponse().getContentAsString(), "$.reservation_id");
		String replayReservationId = com.jayway.jsonpath.JsonPath.read(
				replay.getResponse().getContentAsString(), "$.reservation_id");
		org.junit.jupiter.api.Assertions.assertEquals(originalReservationId, replayReservationId);

		mockMvc.perform(post("/shows/{id}/reserve", showId)
						.with(httpBasic("alice", "alice-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"seats":["A2"],"idempotency_key":"order-1"}
								"""))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(1))
				.andExpect(jsonPath("$.confirmed_seats").value(1));
	}

	@Test
	void multiSeatReservationIsAllOrNothingAndUserLimitIsEnforced() throws Exception {
		String showId = createShow("A1", "A2", "A3", "A4", "A5", "A6");
		reserve(showId, "bob", "bob-password", "bob-order", "[\"A2\"]")
				.andExpect(status().isCreated());

		mockMvc.perform(post("/shows/{id}/reserve", showId)
						.with(httpBasic("alice", "alice-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"seats":["A1","A2"],"idempotency_key":"partial"}
								"""))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(5))
				.andExpect(jsonPath("$.confirmed_seats").value(1));

		mockMvc.perform(post("/shows/{id}/reserve", showId)
						.with(httpBasic("alice", "alice-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"seats":["A1","A3","A4","A5"],"idempotency_key":"limit"}
								"""))
				.andExpect(status().isCreated());
		reserve(showId, "alice", "alice-password", "over-limit", "[\"A6\"]")
				.andExpect(status().isConflict());
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.confirmed_seats").value(5))
				.andExpect(jsonPath("$.available_seats").value(1));
	}

	@Test
	void onlyReservationOwnerCanCancelAndReleasedSeatCanBeRebooked() throws Exception {
		String showId = createShow("A1");
		MvcResult result = reserve(showId, "alice", "alice-password", "cancel-me", "[\"A1\"]")
				.andExpect(status().isCreated())
				.andReturn();
		String reservationId = com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.reservation_id");

		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
						.with(httpBasic("bob", "bob-password")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/reservations/{id}/cancel", reservationId)
						.with(httpBasic("alice", "alice-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("cancelled"));
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(1))
				.andExpect(jsonPath("$.confirmed_seats").value(0));

		reserve(showId, "bob", "bob-password", "rebook", "[\"A1\"]")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").value("bob"));
	}

	@Test
	void concurrentRequestsForOneSeatProduceOneWinnerAndNoServerErrors() throws Exception {
		String showId = createShow("A1");
		int requestCount = 12;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int index = 0; index < requestCount; index++) {
				int requestIndex = index;
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return reserve(showId, "alice", "alice-password", "storm-" + requestIndex, "[\"A1\"]")
							.andReturn().getResponse().getStatus();
				}));
			}
			ready.await();
			start.countDown();

			int created = 0;
			int conflicts = 0;
			for (Future<Integer> result : results) {
				int responseStatus = result.get().intValue();
				if (responseStatus == 201) {
					created++;
				} else if (responseStatus == 409) {
					conflicts++;
				}
			}
			org.junit.jupiter.api.Assertions.assertEquals(1, created);
			org.junit.jupiter.api.Assertions.assertEquals(requestCount - 1, conflicts);
		} finally {
			executor.shutdownNow();
		}
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(1));
	}

	@Test
	void concurrentRetriesWithSameKeyReturnTheSameReservation() throws Exception {
		String showId = createShow("A1");
		int requestCount = 8;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<MvcResult>> results = new ArrayList<>();
			for (int index = 0; index < requestCount; index++) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return reserve(showId, "alice", "alice-password", "same-key", "[\"A1\"]")
							.andExpect(status().isCreated())
							.andReturn();
				}));
			}
			ready.await();
			start.countDown();

			String expectedId = null;
			for (Future<MvcResult> result : results) {
				String reservationId = com.jayway.jsonpath.JsonPath.read(
						result.get().getResponse().getContentAsString(), "$.reservation_id");
				if (expectedId == null) {
					expectedId = reservationId;
				} else {
					org.junit.jupiter.api.Assertions.assertEquals(expectedId, reservationId);
				}
			}
		} finally {
			executor.shutdownNow();
		}
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(0))
				.andExpect(jsonPath("$.confirmed_seats").value(1));
	}

	@Test
	void concurrentReservationsCannotExceedPerUserLimit() throws Exception {
		String showId = createShow("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8");
		int requestCount = 8;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int index = 0; index < requestCount; index++) {
				int requestIndex = index;
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					String seat = "A" + (requestIndex + 1);
					return reserve(showId, "alice", "alice-password", "limit-" + requestIndex,
							"[\"" + seat + "\"]").andReturn().getResponse().getStatus();
				}));
			}
			ready.await();
			start.countDown();

			int created = 0;
			int conflicts = 0;
			for (Future<Integer> result : results) {
				int responseStatus = result.get().intValue();
				if (responseStatus == 201) {
					created++;
				} else if (responseStatus == 409) {
					conflicts++;
				}
			}
			org.junit.jupiter.api.Assertions.assertEquals(4, created);
			org.junit.jupiter.api.Assertions.assertEquals(requestCount - 4, conflicts);
		} finally {
			executor.shutdownNow();
		}
		mockMvc.perform(get("/shows/{id}", showId))
				.andExpect(jsonPath("$.available_seats").value(4))
				.andExpect(jsonPath("$.confirmed_seats").value(4));
	}

	@Test
	void rejectsDuplicateSeatLabels() throws Exception {
		mockMvc.perform(post("/shows")
						.with(httpBasic("admin", "change-this-local-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"friday-night","seats":["A1","A1"],"price_paise":25000}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void servesSwaggerUiSpecification() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	private String createShow(String... seats) throws Exception {
		MvcResult result = mockMvc.perform(post("/shows")
						.with(httpBasic("admin", "change-this-local-password"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"reservation-test","seats":%s,"price_paise":25000}
								""".formatted(java.util.Arrays.stream(seats)
								.map(seat -> "\"" + seat + "\"")
								.collect(java.util.stream.Collectors.joining(",", "[", "]")))))
				.andExpect(status().isCreated())
				.andReturn();
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions reserve(
			String showId, String username, String password, String key, String seats) throws Exception {
		return mockMvc.perform(post("/shows/{id}/reserve", showId)
				.with(httpBasic(username, password))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"seats":%s,"idempotency_key":"%s","user_id":"spoofed-user"}
						""".formatted(seats, key)));
	}
}
