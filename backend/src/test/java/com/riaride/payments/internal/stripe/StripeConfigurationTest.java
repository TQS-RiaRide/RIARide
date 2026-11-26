package com.riaride.payments.internal.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import com.riaride.payments.PaymentGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// Unit test (Surefire): a missing or non-test Stripe key fails startup, instead of the first
// payment, and the key never appears in the error or in toString.
class StripeConfigurationTest {

  private static final String TEST_KEY = "sk_test_51ConfigurationOnly";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(StripeConfiguration.class);

  @Test
  void shouldCreateGatewayWithTestModeKey() {
    contextRunner
        .withPropertyValues("riaride.payments.stripe.secret-key=" + TEST_KEY)
        .run(context -> assertThat(context).hasSingleBean(PaymentGateway.class));
  }

  @Test
  void shouldFailStartupWhenKeyIsMissing() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void shouldFailStartupWhenKeyIsBlank() {
    contextRunner
        .withPropertyValues("riaride.payments.stripe.secret-key=")
        .run(context -> assertThat(context).hasFailed());
  }

  // Test mode only: a live key would move real money.
  @Test
  void shouldFailStartupWithLiveKeyWithoutShowingIt() {
    var liveKey = "sk_live_51NeverInTests";
    contextRunner
        .withPropertyValues("riaride.payments.stripe.secret-key=" + liveKey)
        .run(context -> {
          assertThat(context).hasFailed();
          var failure = context.getStartupFailure();
          for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            assertThat(String.valueOf(cause.getMessage())).doesNotContain(liveKey);
          }
        });
  }

  @Test
  void shouldHideKeyWhenPropertiesArePrinted() {
    var properties =
        new StripeProperties(
            TEST_KEY, "https://api.stripe.com", Duration.ofSeconds(5), Duration.ofSeconds(20));

    assertThat(properties.toString()).doesNotContain(TEST_KEY);
  }
}
