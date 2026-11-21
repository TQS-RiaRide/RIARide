package com.riaride.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

// Writes 401 and 403 as RFC 9457 problem details, with no internal data (QA Manual 3.1),
// in the same shape as Spring MVC's problem details (no type for about:blank).
class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper json;

	ProblemDetailsSecurityHandler(JsonMapper json) {
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(exception));
		write(request, response, HttpStatus.UNAUTHORIZED, "A valid access token is required.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException exception) throws IOException {
		write(request, response, HttpStatus.FORBIDDEN, "You are not allowed to perform this action.");
	}

	// RFC 6750: a rejected token is reported as invalid_token; a missing one gets a bare challenge.
	// The description is fixed, so it never repeats details of the token.
	private static String challenge(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException) {
			return "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or has expired\"";
		}
		return "Bearer";
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
		String detail) throws IOException {
		var problem = new LinkedHashMap<String, Object>();
		problem.put("title", status.getReasonPhrase());
		problem.put("status", status.value());
		problem.put("detail", detail);
		problem.put("instance", request.getRequestURI());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		json.writeValue(response.getOutputStream(), problem);
	}

}
