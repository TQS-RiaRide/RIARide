package com.riaride.payments.internal.stripe;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// Stripe test mode settings, validated at startup. The secret key comes from the environment
// (STRIPE_SECRET_KEY), never from the repository. apiBase is overridden only by tests, to point
// at WireMock.
@ConfigurationProperties("riaride.payments.stripe")
@Validated
record StripeProperties(
    @NotBlank String secretKey,
    @DefaultValue("https://api.stripe.com") String apiBase,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("20s") Duration readTimeout) {

  static final String TEST_MODE_PREFIX = "sk_test_";

  @Override
  public String toString() {
    return "StripeProperties[secretKey=********, apiBase="
        + apiBase
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + "]";
  }
}
