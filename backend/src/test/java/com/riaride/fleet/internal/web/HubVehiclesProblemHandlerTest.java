package com.riaride.fleet.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// Unit test (Surefire): the problem detail for a path or query parameter that cannot be converted
// (RIA-53). Each one names the parameter it is about; none repeats the submitted value.
class HubVehiclesProblemHandlerTest {

	private final HubVehiclesProblemHandler handler = new HubVehiclesProblemHandler();

	private static MethodArgumentTypeMismatchException mismatchOf(String parameter) {
		var exception = mock(MethodArgumentTypeMismatchException.class);
		when(exception.getName()).thenReturn(parameter);
		return exception;
	}

	@Requirement("RIA-53")
	@Test
	void shouldNameTheHubIdWhenItIsNotAUuid() {
		var problem = handler.invalidArgument(mismatchOf("hubId"));

		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getTitle()).isEqualTo("Invalid hub id");
	}

	@Requirement("RIA-53")
	@Test
	void shouldNameTheTypeWhenItIsUnknown() {
		var problem = handler.invalidArgument(mismatchOf("type"));

		assertThat(problem.getTitle()).isEqualTo("Invalid vehicle type");
		assertThat(problem.getProperties()).containsKey("invalid-params");
	}

	// A parameter added later, such as paging, must not be reported as an invalid hub id.
	@Requirement("RIA-53")
	@Test
	void shouldNameAnyOtherParameterGenerically() {
		var problem = handler.invalidArgument(mismatchOf("page"));

		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getTitle()).isEqualTo("Invalid parameter");
		assertThat(problem.getProperties()).containsEntry("invalid-params",
			List.of(Map.of("name", "page", "reason", "The value does not have the expected type or format.")));
	}

}
