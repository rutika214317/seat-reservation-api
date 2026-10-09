package com.example.seatreservation.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
	private static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = parseRequestId(request.getHeader(REQUEST_ID_HEADER));
		long startNanos = System.nanoTime();
		MDC.put("requestId", requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
			LOGGER.atInfo()
					.addKeyValue("event", "http_request")
					.addKeyValue("request_id", requestId)
					.addKeyValue("method", request.getMethod())
					.addKeyValue("path", request.getRequestURI())
					.addKeyValue("status", response.getStatus())
					.addKeyValue("duration_ms", durationMillis)
					.log("HTTP request completed");
			MDC.remove("requestId");
		}
	}

	private String parseRequestId(String supplied) {
		if (supplied != null) {
			try {
				return UUID.fromString(supplied).toString();
			} catch (IllegalArgumentException ignored) {
				// Invalid client values are replaced rather than reflected in logs.
			}
		}
		return UUID.randomUUID().toString();
	}
}
