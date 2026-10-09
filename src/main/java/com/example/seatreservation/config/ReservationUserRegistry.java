package com.example.seatreservation.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ReservationUserRegistry {

	private final String adminUsername;
	private final Map<String, String> credentials;

	public ReservationUserRegistry(String adminUsername, String adminPassword, String reservationUsers) {
		this.adminUsername = adminUsername;
		Map<String, String> configured = new LinkedHashMap<>();
		configured.put(adminUsername, adminPassword);
		for (String value : reservationUsers.split(",")) {
			String[] pair = value.trim().split(":", 2);
			if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
				throw new IllegalArgumentException(
						"Reservation users must be configured as comma-separated username:password pairs");
			}
			if (configured.putIfAbsent(pair[0], pair[1]) != null) {
				throw new IllegalArgumentException("Configured usernames must be unique");
			}
		}
		this.credentials = Collections.unmodifiableMap(configured);
	}

	public String adminUsername() {
		return adminUsername;
	}

	public Map<String, String> credentials() {
		return credentials;
	}

	public Set<String> usernames() {
		return credentials.keySet();
	}
}
