package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.riaride.MqttContracts;
import com.riaride.TestClock;
import com.riaride.TestMosquitto;
import com.riaride.TestMosquitto.CommandRecorder;
import com.riaride.TestMosquitto.RecordedCommand;
import com.riaride.trips.internal.application.UnlockTimeoutJob;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for RIA-54 (start a trip now) and RIA-55 (end a trip at a hub), against the API, the MQTT
// broker and the database. The simulator is played by the steps: they listen on the command topics
// and publish its telemetry. Time moves only when a step moves the TestClock, so the 30-second
// timeout needs no sleep.
public class TripSteps {

  // The telemetry lag bound of RIA-58.
  private static final Duration TELEMETRY_LAG = Duration.ofSeconds(5);
  private static final Duration BOOKING_HOLD = Duration.ofMinutes(15);
  private static final Map<String, String> TRIP_STATUSES =
      Map.of("starting", "STARTING", "in progress", "IN_USE", "failed", "FAILED", "ended", "ENDED");

  private final ScenarioContext context;
  private final JdbcTemplate jdbc;
  private final TestClock clock;
  private final UnlockTimeoutJob unlockTimeoutJob;

  private CommandRecorder commands;
  private String tripLocation;

  public TripSteps(
      ScenarioContext context,
      JdbcTemplate jdbc,
      TestClock clock,
      UnlockTimeoutJob unlockTimeoutJob) {
    this.context = context;
    this.jdbc = jdbc;
    this.clock = clock;
    this.unlockTimeoutJob = unlockTimeoutJob;
  }

  // Before the vehicles (7000) and passes (8000) are removed, since trips and reservations
  // reference
  // them. After each scenario too, ahead of FleetSteps' vehicle cleanup: After hooks with a higher
  // order run first.
  @Before(order = 6500)
  @After(order = 11000)
  public void deleteTripsAndReservations() {
    jdbc.update("delete from trip");
    jdbc.update("delete from reservation");
  }

  @Before
  public void resetClock() {
    clock.reset();
  }

  @After
  public void stopListeningForCommands() {
    if (commands != null) {
      commands.close();
    }
  }

  @Given("the guest has no trip in progress")
  public void theGuestHasNoTripInProgress() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from trip where status in ('STARTING', 'IN_USE')", Integer.class))
        .isZero();
  }

  // Only a trip that is starting can be set up through the API alone.
  @Given("the guest has a {string} trip with bike {string}")
  public void theGuestHasATripWithBike(String status, String code) {
    assertThat(status).isEqualTo("starting");
    startedTrip(code);
  }

  @Given("the guest has a trip in progress with bike {string}")
  public void theGuestHasATripInProgressWithBike(String code) {
    startedTrip(code);
    telemetryReportsThatBikeIsUnlocked(code);
    theTripIs("in progress");
  }

  @When("the guest starts a trip with bike {string}")
  public void theGuestStartsATripWithBike(String code) {
    var response = startTrip(code);
    context.setLastResponse(response);
    if (response.statusCode() == 202) {
      tripLocation = response.header("Location");
    }
  }

  // Published as the simulator answers UNLOCK: unlocked, off its hub, stamped now.
  @When("telemetry reports that bike {string} is unlocked")
  public void telemetryReportsThatBikeIsUnlocked(String code) {
    var battery =
        jdbc.queryForObject("select battery_pct from vehicle where code = ?", Integer.class, code);
    TestMosquitto.publishTelemetry(code, battery, null, "UNLOCKED", clock.instant());
  }

  // The scheduler runs the same job every few seconds on the same clock; the step runs it at once,
  // so the scenario does not wait for it. Whichever runs first fails the trip.
  @When("{int} seconds pass without telemetry confirming the unlock of bike {string}")
  public void secondsPassWithoutTelemetryConfirmingTheUnlock(int seconds, String code) {
    assertThat(
            jdbc.queryForList("select status from trip where vehicle_code = ?", String.class, code))
        .containsExactly("STARTING");
    clock.advance(Duration.ofSeconds(seconds));
    unlockTimeoutJob.failOverdueStarts();
  }

  @When("the guest ends the trip at hub {string}")
  public void theGuestEndsTheTripAtHub(String hubName) {
    assertThat(tripLocation).as("no trip was started in this scenario").isNotNull();
    context.setLastResponse(
        given()
            .auth()
            .oauth2(context.accessToken())
            .contentType(ContentType.JSON)
            .body(Map.of("hubId", hubId(hubName)))
            .when()
            .post(tripLocation + "/end"));
  }

  // Read through the API, as the guest's app follows the trip.
  @Then("the trip is {string}")
  public void theTripIs(String status) {
    var expected = TRIP_STATUSES.get(status);
    assertThat(expected).as("trip status \"%s\"", status).isNotNull();
    // Awaitility polls on its own thread, where the scenario-scoped context is not available.
    var token = context.accessToken();
    await()
        .atMost(TELEMETRY_LAG)
        .untilAsserted(() -> {
          var trip = readTrip(token);
          assertThat(trip.statusCode()).isEqualTo(200);
          assertThat(trip.jsonPath().getString("status")).isEqualTo(expected);
        });
  }

  // Received on the command topic, as the simulator receives it, and checked against the contract.
  @Then("an unlock command for bike {string} is sent to the simulator")
  public void anUnlockCommandForBikeIsSentToTheSimulator(String code) {
    await().atMost(TELEMETRY_LAG).until(() -> unlockFor(code).isPresent());
    assertThat(MqttContracts.commandViolations(unlockFor(code).orElseThrow().payload())).isEmpty();
  }

  @Then("the guest's pass is active")
  public void theGuestsPassIsActive() {
    var pass = passOfTheTrip();
    assertThat(pass.get("status")).isEqualTo("ACTIVE");
    assertThat(pass.get("activated_at")).isNotNull();
  }

  // RIA-87: the time runs from the confirmed unlock, which the TestClock fixes.
  @Then("the guest's pass expires {int} hours after the unlock was confirmed")
  public void theGuestsPassExpiresHoursAfterTheUnlock(int hours) {
    var pass = passOfTheTrip();
    var activatedAt = ((java.sql.Timestamp) pass.get("activated_at")).toInstant();
    assertThat(activatedAt).isEqualTo(clock.instant());
    assertThat(((java.sql.Timestamp) pass.get("expires_at")).toInstant())
        .isEqualTo(activatedAt.plus(Duration.ofHours(hours)));
  }

  @Then("the guest's pass is not active")
  public void theGuestsPassIsNotActive() {
    var pass = passOfTheTrip();
    assertThat(pass.get("status")).isEqualTo("INACTIVE");
    assertThat(pass.get("activated_at")).isNull();
  }

  // BR11 and BR13: a failed start keeps the bike held for the pass until the booking expires.
  @Then("bike {string} is {string} until the booking expires")
  public void bikeIsUntilTheBookingExpires(String code, String status) {
    assertThat(jdbc.queryForObject("select status from vehicle where code = ?", String.class, code))
        .isEqualTo(status.toUpperCase(Locale.ROOT).replace(' ', '_'));
    var reservation =
        jdbc.queryForMap(
            """
            select r.status, r.expires_at, t.requested_at from reservation r join trip t on t.reservation_id = r.id
            where t.id = ?::uuid
            """,
            tripId());
    assertThat(reservation.get("status")).isEqualTo("ACTIVE");
    var requestedAt = ((Timestamp) reservation.get("requested_at")).toInstant();
    assertThat(((Timestamp) reservation.get("expires_at")).toInstant())
        .isEqualTo(requestedAt.plus(BOOKING_HOLD));
  }

  // The hubs of the nearbyHubs member of the last problem detail, by name, for example
  // "Marés Salinas, Marés Centro".
  @Then("the problem detail suggests the hubs {string} in that order")
  public void theProblemDetailSuggestsTheHubsInThatOrder(String names) {
    var expected = Arrays.stream(names.split(",")).map(String::trim).toList();
    assertThat(context.lastResponse().jsonPath().getList("nearbyHubs.name", String.class))
        .containsExactlyElementsOf(expected);
  }

  // Received on the command topic, as the simulator receives it, and checked against the contract.
  @Then("a lock command for bike {string} at hub {string} is sent to the simulator")
  public void aLockCommandForBikeAtHubIsSentToTheSimulator(String code, String hubName) {
    var hubId = hubId(hubName).toString();
    await().atMost(TELEMETRY_LAG).until(() -> lockFor(code, hubId).isPresent());
    assertThat(MqttContracts.commandViolations(lockFor(code, hubId).orElseThrow().payload()))
        .isEmpty();
  }

  private UUID hubId(String hubName) {
    return jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
  }

  private Optional<RecordedCommand> lockFor(String code, String hubId) {
    return commands.commands().stream()
        .filter(command -> command.vehicleCode().equals(code) && "LOCK".equals(command.command()))
        .filter(command -> command.payload().contains("\"" + hubId + "\""))
        .findFirst();
  }

  // Listening starts before the request: the broker does not keep commands for late subscribers.
  private Response startTrip(String code) {
    if (commands == null) {
      commands = TestMosquitto.recordCommands();
    }
    return given()
        .auth()
        .oauth2(context.accessToken())
        .contentType(ContentType.JSON)
        .body(Map.of("vehicleCode", code))
        .when()
        .post("/api/v1/trips");
  }

  private void startedTrip(String code) {
    var response = startTrip(code);
    assertThat(response.statusCode()).isEqualTo(202);
    tripLocation = response.header("Location");
  }

  private Response readTrip(String token) {
    assertThat(tripLocation).as("no trip was started in this scenario").isNotNull();
    return given().auth().oauth2(token).when().get(tripLocation);
  }

  private String tripId() {
    return readTrip(context.accessToken()).jsonPath().getString("id");
  }

  private Map<String, Object> passOfTheTrip() {
    return jdbc.queryForMap(
        """
        select p.status, p.activated_at, p.expires_at from pass p join trip t on t.pass_id = p.id where t.id = ?::uuid
        """,
        tripId());
  }

  private Optional<RecordedCommand> unlockFor(String code) {
    return commands.commands().stream()
        .filter(command -> command.vehicleCode().equals(code) && "UNLOCK".equals(command.command()))
        .findFirst();
  }
}
