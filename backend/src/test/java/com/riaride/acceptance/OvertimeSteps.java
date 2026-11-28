package com.riaride.acceptance;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.riaride.TestClock;
import com.riaride.trips.internal.application.OvertimeRetryJob;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-88 (overtime charge). The guest's card is saved by the purchase (RIA-52); here the
// purchase payment is stored directly, so the scenarios need no purchase through the sandbox.
public class OvertimeSteps {

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private final JdbcTemplate jdbc;
  private final TestClock clock;
  private final OvertimeRetryJob retryJob;

  public OvertimeSteps(JdbcTemplate jdbc, TestClock clock, OvertimeRetryJob retryJob) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.retryJob = retryJob;
  }

  // A publication left by another scenario must not be retried in this one.
  @Before
  public void deleteEventPublications() {
    jdbc.update("delete from event_publication");
  }

  private String passId() {
    return jdbc.queryForObject("select id::text from pass", String.class);
  }

  // The key each charge of this trip's overtime carries to the payment sandbox.
  private String overtimeKey() {
    return "trip-" + jdbc.queryForObject("select id::text from trip", String.class) + ":overtime";
  }

  private int overtimeChargeRequests() {
    return CucumberSpringConfiguration.STRIPE
        .findAll(
            postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
                .withHeader("Idempotency-Key", equalTo(overtimeKey())))
        .size();
  }

  // The listener runs after the commit, on another thread: wait until its attempt has failed.
  private void awaitFailedAttempts(int attempts) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          assertThat(overtimeChargeRequests()).isEqualTo(attempts);
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from event_publication where status = 'FAILED'",
                      Integer.class))
              .isEqualTo(1);
        });
  }

  @Given("the guest's pass was bought with a card saved for off-session charges")
  public void theGuestsPassWasBoughtWithASavedCard() {
    jdbc.update(
        """
        insert into payment (id, pass_id, kind, amount_cents, status, provider_ref, idempotency_key,
                             customer_ref, payment_method_ref)
        values (?::uuid, ?::uuid, 'PURCHASE', 9000, 'SUCCEEDED', 'pi_purchase', ?, 'cus_guest',
                'pm_saved_card')
        """,
        UUID.randomUUID().toString(),
        passId(),
        "purchase-" + UUID.randomUUID());
  }

  @Given("the payment sandbox accepts off-session charges")
  public void thePaymentSandboxAcceptsOffSessionCharges() {
    CucumberSpringConfiguration.STRIPE.stubFor(
        post(PAYMENT_INTENTS)
            .willReturn(
                okJson(
                    """
                    {"id": "pi_overtime", "object": "payment_intent", "status": "succeeded",
                     "currency": "eur", "customer": "cus_guest", "payment_method": "pm_saved_card"}
                    """)));
  }

  @Given("the payment sandbox is down")
  public void thePaymentSandboxIsDown() {
    CucumberSpringConfiguration.STRIPE.stubFor(
        post(PAYMENT_INTENTS)
            .willReturn(
                aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\": {\"type\": \"api_error\", \"message\": \"Something went wrong.\"}}")));
  }

  @Given("the trip has lasted {int} h {int} min")
  public void theTripHasLasted(int hours, int minutes) {
    clock.advance(Duration.ofHours(hours).plusMinutes(minutes));
  }

  @Then("no overtime has been charged yet")
  public void noOvertimeHasBeenChargedYet() {
    awaitFailedAttempts(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payment where kind = 'OVERTIME'", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null",
                Integer.class))
        .isEqualTo(1);
  }

  // The retry job runs on the TestClock, so the backoff passes without a sleep.
  @When("the payment sandbox is back and the overtime charge is retried")
  public void thePaymentSandboxIsBackAndTheChargeIsRetried() {
    // The mappings only: the request journal keeps the failed attempt for the check below.
    CucumberSpringConfiguration.STRIPE.resetMappings();
    thePaymentSandboxAcceptsOffSessionCharges();
    clock.advance(Duration.ofMinutes(5));
    retryJob.retryFailedCharges();
    // The retry went to the sandbox with the same key as the failed attempt, so it cannot charge
    // twice.
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(overtimeChargeRequests()).isEqualTo(2));
  }

  // The job waits 1, 2, 4 and 8 minutes between attempts; the TestClock passes them at once.
  @When("the overtime charge is retried until its last attempt fails")
  public void theOvertimeChargeIsRetriedUntilItsLastAttemptFails() {
    awaitFailedAttempts(1);
    for (var attempt = 2; attempt <= 5; attempt++) {
      clock.advance(Duration.ofMinutes(10));
      retryJob.retryFailedCharges();
      awaitFailedAttempts(attempt);
    }
    clock.advance(Duration.ofMinutes(20));
    retryJob.retryFailedCharges();
  }

  @Then("the overtime of {word} EUR is recorded as unpaid")
  public void theOvertimeIsRecordedAsUnpaid(String euros) {
    var cents = new BigDecimal(euros).movePointRight(2).longValueExact();
    assertThat(
            jdbc.queryForList(
                "select amount_cents from payment where kind = 'OVERTIME' and status = 'FAILED' and idempotency_key = ?",
                Long.class,
                overtimeKey()))
        .containsExactly(cents);
    assertThat(overtimeChargeRequests()).isEqualTo(5);
  }

  @Then("no overtime charge is left to retry")
  public void noOvertimeChargeIsLeftToRetry() {
    assertThat(jdbc.queryForObject("select count(*) from event_publication", Integer.class))
        .isZero();
  }

  @Then("the guest is charged {int} blocks of 15 minutes of overtime, {word} EUR, off-session")
  public void theGuestIsChargedOvertime(int blocks, String euros) {
    var cents = new BigDecimal(euros).movePointRight(2).intValueExact();
    assertThat(cents).isEqualTo(blocks * 200);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() ->
            assertThat(
                    jdbc.queryForList(
                        "select amount_cents from payment where kind = 'OVERTIME' and status = 'SUCCEEDED'",
                        Long.class))
                .containsExactly((long) cents));
    CucumberSpringConfiguration.STRIPE.verify(
        postRequestedFor(urlPathEqualTo(PAYMENT_INTENTS))
            .withHeader("Idempotency-Key", equalTo(overtimeKey()))
            .withRequestBody(containing("amount=" + cents))
            .withRequestBody(containing("customer=cus_guest"))
            .withRequestBody(containing("payment_method=pm_saved_card"))
            .withRequestBody(containing("off_session=true")));
  }
}
