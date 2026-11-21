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

	// PostgreSQL rejects NUL, and no control character belongs in a name.
	private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");

	@Id
	private UUID id;

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
	@Transient
	private boolean isNew;

	protected Hub() {
		// for JPA
	}

	private Hub(String name, HubKind kind, UUID hotelId, double latitude, double longitude, int capacity) {
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
	// The name is stored without leading and trailing spaces.
	public static Hub register(String name, HubKind kind, UUID hotelId, double latitude, double longitude,
		int capacity) {
		var strippedName = validName(name);
		validateKindAndHotel(kind, hotelId);
		validatePosition(latitude, longitude);
		if (capacity < MIN_CAPACITY) {
			throw new InvalidHubException("capacity", "A hub must have room for at least " + MIN_CAPACITY + " bike.");
		}
		return new Hub(strippedName, kind, hotelId, latitude, longitude, capacity);
	}

	private static String validName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidHubException("name", "A hub needs a name.");
		}
		var stripped = name.strip();
		if (stripped.length() > MAX_NAME_LENGTH) {
			throw new InvalidHubException("name",
				"A hub name must have at most " + MAX_NAME_LENGTH + " characters.");
		}
		if (CONTROL_CHARACTER.matcher(stripped).find()) {
			throw new InvalidHubException("name", "A hub name must not contain control characters.");
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
		if (kind == HubKind.TOURIST_SPOT && hotelId != null) {
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

	// Bikes cannot be registered yet (RIA-57), so no hub has any docked. Once they can, this
	// counts the bikes docked at the hub.
	public int getDockedBikes() {
		return 0;
	}

	public int getFreeSlots() {
		return capacity - getDockedBikes();
	}

}
