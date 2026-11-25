package com.riaride;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Inserts the rows an integration test needs with plain SQL, so a test of one module does not
// depend on another module's domain classes. Synthetic data only.
public final class TestRows {

  public static final String MARES_CENTRO = "00000000-0000-0000-0000-000000000001";

  private TestRows() {}

  // stayCode: 8 characters, unique per test.
  public static UUID stay(JdbcTemplate jdbc, String stayCode) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into stay (id, hotel_id, guest_name, stay_code, check_in, check_out, status)
        values (?, ?::uuid, 'Rita Almeida', ?, date '2026-11-28', date '2026-12-02', 'ACTIVE')
        """,
        id,
        MARES_CENTRO,
        stayCode);
    return id;
  }

  public static UUID guest(JdbcTemplate jdbc, String stayCode, String email) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into guest (id, keycloak_id, stay_id, email, locale)
        values (?, ?, ?, ?, 'pt')
        """,
        id,
        UUID.randomUUID().toString(),
        stay(jdbc, stayCode),
        email);
    return id;
  }

  public static UUID pass(JdbcTemplate jdbc, UUID guestId, String status) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into pass (id, guest_id, pass_type, status, price_cents, deposit_cents, deposit_status)
        values (?, ?, 'STAY', ?, 4000, 5000, 'HELD')
        """,
        id,
        guestId,
        status);
    return id;
  }

  public static UUID touristSpot(JdbcTemplate jdbc, String name, int capacity) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into hub (id, name, kind, latitude, longitude, capacity)
        values (?, ?, 'TOURIST_SPOT', 40.64, -8.65, ?)
        """,
        id,
        name,
        capacity);
    return id;
  }

  // hubId null: the vehicle is being ridden.
  public static UUID vehicle(JdbcTemplate jdbc, String code, String status, UUID hubId) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        insert into vehicle (id, code, type, status, hub_id, battery_pct)
        values (?, ?, 'E_BIKE', ?, ?, 80)
        """,
        id,
        code,
        status,
        hubId);
    return id;
  }
}
