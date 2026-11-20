package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.fleet.internal.domain.BookingBatteryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Unit test (Surefire): the minimum battery for booking (BR12) comes from configuration,
// so the backoffice usage rule can replace it later without touching the policy.
class FleetConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(FleetConfiguration.class);

	@Requirement("RIA-59")
	@Test
	void shouldBuildBookingPolicyFromConfiguredMinimumBattery() {
		contextRunner.withPropertyValues("riaride.fleet.min-booking-battery-pct=20")
			.run(context -> assertThat(context.getBean(BookingBatteryPolicy.class).minimumPercent())
				.isEqualTo(20));
	}

}
