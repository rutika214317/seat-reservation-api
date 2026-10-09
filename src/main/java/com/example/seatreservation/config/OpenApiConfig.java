package com.example.seatreservation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI seatReservationOpenApi() {
		return new OpenAPI()
				.components(new Components()
						.addSecuritySchemes("basicAuth", new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("basic")));
	}
}
