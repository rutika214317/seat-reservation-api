package com.example.seatreservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"app.security.basic.username=admin",
		"app.security.basic.password=change-this-local-password"
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
}
