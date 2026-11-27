package com.riaride.fleet.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Persistable;

// A place where bikes are docked and where trips start and end (RIA-56): at a hotel of the group
// or at a tourist spot, with a position in decimal degrees and room for a number of bikes (BR10).
@Entity
@Table(name = "hub")
public class Hub implements Persistable<UUID> {

  public static final int MAX_NAME_LENGTH = 120;
  static final int MIN_CAPACITY = 1;
  static final double MAX_LATITUDE = 90.0;
  static final double MAX_LONGITUDE = 180.0;
  // The mean radius of the Earth, which the haversine formula assumes is a sphere.
  static final double EARTH_RADIUS_METERS = 6_371_000;

  // No invisible character belongs in a name: no C0 or C1 control character (Unicode category
  // Cc, which includes the NUL that PostgreSQL rejects) and no format character (category Cf, such
  // as the zero-width space or the right-to-left override).
  private static final Pattern INVISIBLE_CHARACTER = Pattern.compile("[\\p{Cc}\\p{Cf}]");

  @Id private UUID id;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private HubKind kind;

  @Column(name = "hotel_id", updatable = false)
  private UUID hotelId;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(nullable = false)
  private int capacity;

  // The id is assigned in the domain, so JPA cannot tell a new hub from an existing one.
  @Transient private boolean isNew;

  protected Hub() {
    // for JPA
  }

  private Hub(
      String name, HubKind kind, UUID hotelId, double latitude, double longitude, int capacity) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.kind = kind;
    this.hotelId = hotelId;
    this.latitude = latitude;
    this.longitude = longitude;
    this.capacity = capacity;
    this.isNew = true;
  }

  // hotelId is the hotel a HOTEL hub belongs to, and null for a TOURIST_SPOT hub.
  // The name is stored without leading and trailing spaces (see Names.stripSpaces).
  public static Hub register(
      String name, HubKind kind, UUID hotelId, double latitude, double longitude, int capacity) {
    var strippedName = validName(name);
    validateKindAndHotel(kind, hotelId);
    validatePosition(latitude, longitude);
    requireValidCapacity(capacity);
    return new Hub(strippedName, kind, hotelId, latitude, longitude, capacity);
  }

  // BR10: a hub has room for at least one bike, when registered and whenever its capacity changes.
  public static void requireValidCapacity(int capacity) {
    if (capacity < MIN_CAPACITY) {
      throw new InvalidHubException(
          "capacity", "A hub must have room for at least " + MIN_CAPACITY + " bike.");
    }
  }

  // The hotel group sets how many bikes a hub takes (RIA-96, BR10). The fleet module checks, under
  // the hub's lock, that the bikes already docked there still fit.
  public void changeCapacity(int capacity) {
    requireValidCapacity(capacity);
    this.capacity = capacity;
  }

  private static String validName(String name) {
    var stripped = name == null ? "" : Names.stripSpaces(name);
    if (stripped.isEmpty()) {
      throw new InvalidHubException("name", "A hub needs a name.");
    }
    if (stripped.length() > MAX_NAME_LENGTH) {
      throw new InvalidHubException(
          "name", "A hub name must have at most " + MAX_NAME_LENGTH + " characters.");
    }
    if (INVISIBLE_CHARACTER.matcher(stripped).find()) {
      throw new InvalidHubException(
          "name", "A hub name must not contain control or format characters.");
    }
    return stripped;
  }

  private static void validateKindAndHotel(HubKind kind, UUID hotelId) {
    if (kind == null) {
      throw new InvalidHubException("kind", "A hub must be at a hotel or at a tourist spot.");
    }
    if (kind == HubKind.HOTEL && hotelId == null) {
      throw new InvalidHubException("hotel", "A hotel hub must name the hotel it belongs to.");
    }
    requireNoHotelForTouristSpot(kind, hotelId != null);
  }

  // A tourist spot hub belongs to no hotel. Callers that resolve the hotel from its name check
  // this first, so the kind decides whether a hotel is allowed before the hotel is looked up.
  public static void requireNoHotelForTouristSpot(HubKind kind, boolean hasHotel) {
    if (kind == HubKind.TOURIST_SPOT && hasHotel) {
      throw new InvalidHubException("hotel", "A tourist spot hub must not belong to a hotel.");
    }
  }

  // Comparisons are false for NaN, so NaN is rejected along with the infinities.
  private static void validatePosition(double latitude, double longitude) {
    if (!(latitude >= -MAX_LATITUDE && latitude <= MAX_LATITUDE)) {
      throw new InvalidHubException("latitude", "The latitude must be from -90 to 90 degrees.");
    }
    if (!(longitude >= -MAX_LONGITUDE && longitude <= MAX_LONGITUDE)) {
      throw new InvalidHubException("longitude", "The longitude must be from -180 to 180 degrees.");
    }
  }

  // The straight-line (great-circle) distance to another hub, by the haversine formula. A full hub
  // uses it to suggest the nearest ones (RIA-55, architecture section 7). At the scale of a city
  // the sphere is accurate to well under 1 %.
  public double metersTo(Hub other) {
    var fromLatitude = Math.toRadians(latitude);
    var toLatitude = Math.toRadians(other.latitude);
    var latitudeDelta = toLatitude - fromLatitude;
    var longitudeDelta = Math.toRadians(other.longitude - longitude);
    var haversine =
        Math.pow(Math.sin(latitudeDelta / 2), 2)
            + Math.cos(fromLatitude)
                * Math.cos(toLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(haversine));
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  public String getName() {
    return name;
  }

  public HubKind getKind() {
    return kind;
  }

  public UUID getHotelId() {
    return hotelId;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public int getCapacity() {
    return capacity;
  }
}
