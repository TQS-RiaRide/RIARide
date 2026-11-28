package com.riaride.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.riaride.FixedClockConfiguration;
import com.riaride.TestClock;
import com.riaride.trips.internal.application.EndedPassClosingJob;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-89 (pass ends during a trip). Times are local times in Vila Ria, as the scenarios
// state them; the TestClock is moved forward to them, so no scenario waits.
public class PassEndSteps {

  private static final DateTimeFormatter VILA_RIA_TIME =
      DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH);

  private final JdbcTemplate jdbc;
  private final TestClock clock;
  private final EndedPassClosingJob closingJob;

  public PassEndSteps(JdbcTemplate jdbc, TestClock clock, EndedPassClosingJob closingJob) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.closingJob = closingJob;
  }

  private static Instant inVilaRia(String time) {
    return LocalDateTime.parse(time, VILA_RIA_TIME)
        .atZone(FixedClockConfiguration.VILA_RIA)
        .toInstant();
  }

  // The clock only moves forward, as time does for the application.
  @Given("it is {string} in Vila Ria")
  public void itIsInVilaRia(String time) {
    var gap = Duration.between(clock.instant(), inVilaRia(time));
    assertThat(gap.isNegative()).as("the clock cannot go back to %s", time).isFalse();
    clock.advance(gap);
  }

  // A pass already in use: its first trip was 24 hours (1-day) or 72 hours (3-day) before it
  // expires. It keeps the end of the guest's stay, as a purchase stores it (RIA-87).
  @Given("the guest has an active {string} pass that expires at {string}")
  public void theGuestHasAnActivePassThatExpiresAt(String passType, String time) {
    var expiresAt = inVilaRia(time);
    var lasts =
        switch (passType) {
          case "1-day" -> Duration.ofHours(24);
          case "3-day" -> Duration.ofHours(72);
          default -> throw new IllegalArgumentException("No fixed duration for " + passType);
        };
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status,
                          activated_at, expires_at, stay_ends_at)
        select ?::uuid, g.id, p.pass_type, 'ACTIVE', p.price_cents, p.deposit_cents, 'HELD',
               ?::timestamptz, ?::timestamptz,
               (s.check_out + 1)::timestamp at time zone 'Europe/Lisbon'
        from pass_price p, guest g join stay s on s.id = g.stay_id
        where p.pass_type = ?
        """,
        UUID.randomUUID().toString(),
        expiresAt.minus(lasts).toString(),
        expiresAt.toString(),
        "1-day".equals(passType) ? "ONE_DAY" : "THREE_DAY");
  }

  // The scheduler runs the job on the same clock; the step runs it at once, so the scenario does
  // not wait for it.
  @When("the job that closes ended passes runs")
  public void theJobThatClosesEndedPassesRuns() {
    closingJob.closeEndedPasses();
  }

  // The deposit is refunded or withheld at the end of the pass by RIA-114; until then it is held.
  @Then("the guest's pass is closed, with its deposit still held")
  public void theGuestsPassIsClosedWithItsDepositStillHeld() {
    var pass = jdbc.queryForMap("select status, deposit_status from pass");
    assertThat(pass.get("status")).isEqualTo("CLOSED");
    assertThat(pass.get("deposit_status")).isEqualTo("HELD");
  }
}
