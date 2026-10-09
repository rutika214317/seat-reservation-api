package com.example.seatreservation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/swagger-ui.html",
								"/swagger-ui/**",
								"/v3/api-docs/**",
								"/actuator/health",
								"/actuator/health/**",
								"/actuator/prometheus")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/shows").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/shows/**").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	ReservationUserRegistry reservationUserRegistry(
			@Value("${app.security.basic.username}") String username,
			@Value("${app.security.basic.password}") String password,
			@Value("${app.security.reservation-users:user:change-this-local-password}") String reservationUsers) {
		return new ReservationUserRegistry(username, password, reservationUsers);
	}

	@Bean
	UserDetailsService userDetailsService(
			ReservationUserRegistry registry,
			PasswordEncoder passwordEncoder) {
		List<org.springframework.security.core.userdetails.UserDetails> users = new ArrayList<>();
		registry.credentials().forEach((username, password) -> {
			var builder = User.withUsername(username).password(passwordEncoder.encode(password));
			if (username.equals(registry.adminUsername())) {
				builder.roles("ADMIN", "USER");
			} else {
				builder.roles("USER");
			}
			users.add(builder.build());
		});
		return new InMemoryUserDetailsManager(users);
	}
}
