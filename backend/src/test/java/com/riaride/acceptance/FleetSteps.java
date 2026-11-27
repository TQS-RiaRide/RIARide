package com.riaride.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.riaride.TestMosquitto;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Steps for the fleet's vehicles and their telemetry (RIA-59, RIA-53), reusable by RIA-58, and for
// hubs with a given position, capacity and occupancy (RIA-55).
public class FleetSteps {

  // The telemetry lag bound of RIA-58.
  private static final Duration TELEMETRY_LAG = Duration.ofSeconds(5);

  private final ScenarioContext context;
  private final JdbcTemplate jdbc;

  public FleetSteps(ScenarioContext context, JdbcTemplate jdbc) {
    this.context = context;
    this.jdbc = jdbc;
  }

  // Before the hubs are removed (HubSteps, default order): vehicles reference hubs.
  // Also after each scenario, so no vehicle is left to block a later "delete from hub".
  @Before(order = 7000)
  @After
  public void removeVehicles() {
    jdbc.update("delete from vehicle");
  }

  // A hub at the hotel of the same name, with one available e-bike docked there. Hub names are
  // unique, so a second bike at a hub the scenario already has is docked at that hub (RIA-54).
  @Given("a hub {string} with an available bike {string} with {int} % battery")
  public void aHubWithAnAvailableBike(String hubName, String code, int batteryPct) {
    insertBike(code, "AVAILABLE", batteryPct, aHotelHub(hubName));
  }

  // A hub at the hotel of the same name, with the e-bikes of the table docked there. Columns:
  // code, status ("available", "in use", ...) and battery (%). A bike in use is out on a trip, so
  // it is not docked and takes no slot.
  @Given("a hub {string} with these bikes:")
  public void aHubWithTheseBikes(String hubName, DataTable bikes) {
    var hubId = aHotelHub(hubName);
    for (var bike : bikes.asMaps()) {
      var status = bike.get("status").trim().toUpperCase(Locale.ROOT).replace(' ', '_');
      var dockedAt = "IN_USE".equals(status) ? null : hubId;
      insertBike(bike.get("code"), status, Integer.valueOf(bike.get("battery")), dockedAt);
    }
  }

  // Hubs at the hotels of the same name. Columns: name, latitude, longitude, capacity and docked
  // (available bikes docked there). A hub the scenario already has takes the table's position and
  // capacity, and keeps the bikes it has, so docked counts them too.
  @Given("these hubs:")
  public void theseHubs(DataTable hubs) {
    for (var hub : hubs.asMaps()) {
      var hubId =
          aHotelHubAt(
              hub.get("name"),
              Double.parseDouble(hub.get("latitude")),
              Double.parseDouble(hub.get("longitude")),
              Integer.parseInt(hub.get("capacity")));
      dockBikes(hubId, Integer.parseInt(hub.get("docked")));
    }
  }

  private UUID aHotelHubAt(String hubName, double latitude, double longitude, int capacity) {
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        select ?, name, 'HOTEL', id, ?, ?, ? from hotel where name = ?
        on conflict (name) do update
        set latitude = excluded.latitude, longitude = excluded.longitude, capacity = excluded.capacity
        """,
        UUID.randomUUID(),
        latitude,
        longitude,
        capacity,
        hubName);
    return jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
  }

  // Tops the hub up to that many docked bikes, with codes no scenario uses.
  private void dockBikes(UUID hubId, int docked) {
    var already =
        jdbc.queryForObject("select count(*) from vehicle where hub_id = ?", Integer.class, hubId);
    assertThat(already).as("bikes already docked at the hub").isLessThanOrEqualTo(docked);
    for (var i = already; i < docked; i++) {
      insertBike("DK-" + UUID.randomUUID().toString().substring(0, 8), "AVAILABLE", 80, hubId);
    }
  }

  private UUID aHotelHub(String hubName) {
    jdbc.update(
        """
        insert into hub (id, name, kind, hotel_id, latitude, longitude, capacity)
        select ?, name, 'HOTEL', id, 40.6405, -8.6538, 10 from hotel where name = ?
        on conflict (name) do nothing
        """,
        UUID.randomUUID(),
        hubName);
    return jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
  }

  private void insertBike(String code, String status, int batteryPct, UUID hubId) {
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', ?, ?, ?)
        """,
        UUID.randomUUID(),
        code,
        status,
        hubId,
        batteryPct);
  }

  // Published over MQTT as the simulator does; the step returns once the platform has stored the
  // reading, so the next step sees it.
  @When("telemetry reports that bike {string} has {int} % battery")
  public void telemetryReportsBattery(String code, int batteryPct) {
    var hubId = jdbc.queryForObject("select hub_id from vehicle where code = ?", UUID.class, code);
    TestMosquitto.publishTelemetry(code, batteryPct, Objects.toString(hubId, null));
    await()
        .atMost(TELEMETRY_LAG)
        .until(() ->
            Integer.valueOf(batteryPct)
                .equals(
                    jdbc.queryForObject(
                        "select battery_pct from vehicle where code = ?", Integer.class, code)));
  }

  @When("the guest lists the bikes at hub {string}")
  public void theGuestListsTheBikesAtHub(String hubName) {
    var hubId = jdbc.queryForObject("select id from hub where name = ?", UUID.class, hubName);
    context.setLastResponse(
        given()
            .auth()
            .oauth2(context.accessToken())
            .when()
            .get("/api/v1/hubs/{hubId}/vehicles", hubId));
  }

  // The codes of the bookable bikes in the last listing, in the order listed, for example
  // "EB-101, EB-102".
  @Then("the bookable bikes are {string}")
  public void theBookableBikesAre(String codes) {
    var response = context.lastResponse();
    assertThat(response.statusCode()).isEqualTo(200);
    var expected = Arrays.stream(codes.split(",")).map(String::trim).toList();
    assertThat(response.jsonPath().getList("findAll { it.bookable }.code", String.class))
        .containsExactlyElementsOf(expected);
  }

  @Then("bike {string} is not bookable")
  public void bikeIsNotBookable(String code) {
    var response = context.lastResponse();
    assertThat(response.statusCode()).isEqualTo(200);
    var bookable =
        response
            .jsonPath()
            .getList("findAll { it.code == '" + code + "' }.bookable", Boolean.class);
    assertThat(bookable).containsExactly(false);
  }
}
