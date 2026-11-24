package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// Unit test (Surefire): a hub is at a hotel or at a tourist spot, has a position in decimal
// degrees and room for at least one bike (BR10). A new hub has no bikes docked.
// Name uniqueness needs the other hubs, so it is tested in RegisterHubApiIT, not here.
// Each rejected value names the request field it came from, so the API can report it.
class HubTest {

	private static final UUID MARES_PRAIA = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final String NAME = "Marés Praia – Beach Front";
	private static final double LATITUDE = 40.6405;
	private static final double LONGITUDE = -8.7538;

	private static Hub hotelHubWithCapacity(int capacity) {
		return Hub.register(NAME, HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, capacity);
	}

	private static Hub hotelHubAt(double latitude, double longitude) {
		return Hub.register(NAME, HubKind.HOTEL, MARES_PRAIA, latitude, longitude, 10);
	}

	private static void assertRejectsField(Runnable registration, String field) {
		assertThatThrownBy(registration::run)
			.isInstanceOfSatisfying(InvalidHubException.class,
				invalid -> assertThat(invalid.getField()).isEqualTo(field));
	}

	@Requirement("RIA-56")
	@Test
	void shouldRegisterHotelHubWithGivenDetails() {
		var hub = hotelHubWithCapacity(10);

		assertThat(hub.getId()).isNotNull();
		assertThat(hub.getName()).isEqualTo(NAME);
		assertThat(hub.getKind()).isEqualTo(HubKind.HOTEL);
		assertThat(hub.getHotelId()).isEqualTo(MARES_PRAIA);
		assertThat(hub.getLatitude()).isEqualTo(LATITUDE);
		assertThat(hub.getLongitude()).isEqualTo(LONGITUDE);
		assertThat(hub.getCapacity()).isEqualTo(10);
	}

	@Requirement("RIA-56")
	@Test
	void shouldGiveEachRegisteredHubItsOwnId() {
		assertThat(hotelHubWithCapacity(10).getId()).isNotEqualTo(hotelHubWithCapacity(10).getId());
	}

	@Requirement("RIA-56")
	@Test
	void shouldRegisterNewHubWithNoBikesDockedAndAllSlotsFree() {
		var hub = hotelHubWithCapacity(10);

		assertThat(hub.getDockedBikes()).isZero();
		assertThat(hub.getFreeSlots()).isEqualTo(10);
	}

	@Requirement("RIA-56")
	@Test
	void shouldRegisterTouristSpotHubWithoutHotel() {
		var hub = Hub.register("Miradouro das Salinas", HubKind.TOURIST_SPOT, null, 40.6425, -8.7479, 6);

		assertThat(hub.getKind()).isEqualTo(HubKind.TOURIST_SPOT);
		assertThat(hub.getHotelId()).isNull();
	}

	// BR10: capacity is at least one bike slot.

	@Requirement("RIA-56")
	@Test
	void shouldAcceptHubWithCapacity1() {
		var hub = hotelHubWithCapacity(1);

		assertThat(hub.getCapacity()).isEqualTo(1);
		assertThat(hub.getFreeSlots()).isEqualTo(1);
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectHubWithCapacity0() {
		assertRejectsField(() -> hotelHubWithCapacity(0), "capacity");
	}

	@Requirement("RIA-56")
	@ParameterizedTest
	@ValueSource(ints = { -1, Integer.MIN_VALUE })
	void shouldRejectHubWithNegativeCapacity(int capacity) {
		assertRejectsField(() -> hotelHubWithCapacity(capacity), "capacity");
	}

	// Name: required, not blank.

	@Requirement("RIA-56")
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", " ", "\t" })
	void shouldRejectHubWithoutName(String name) {
		assertRejectsField(() -> Hub.register(name, HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10), "name");
	}

	// Name: at most 120 characters.

	@Requirement("RIA-56")
	@Test
	void shouldAcceptHubNameOf120Characters() {
		var name = "A".repeat(120);

		var hub = Hub.register(name, HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo(name);
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectHubNameOf121Characters() {
		assertRejectsField(
			() -> Hub.register("B".repeat(121), HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10), "name");
	}

	// Leading and trailing spaces are removed before the name is validated and stored.

	@Requirement("RIA-56")
	@Test
	void shouldStoreHubNameWithoutSurroundingSpaces() {
		var hub = Hub.register("  " + NAME + "  ", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo(NAME);
	}

	@Requirement("RIA-56")
	@Test
	void shouldAcceptHubNameOf120CharactersSurroundedBySpaces() {
		var name = "A".repeat(120);

		var hub = Hub.register("  " + name + "  ", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo(name);
	}

	// Every Unicode separator (category Z) counts as a space at the ends of a name, not only the
	// ASCII whitespace that String.strip removes: the no-break space U+00A0, the figure space
	// U+2007, the narrow no-break space U+202F, the ideographic space U+3000 and the line and
	// paragraph separators U+2028 and U+2029.

	@Requirement("RIA-56")
	@ParameterizedTest(name = "U+{0}")
	@ValueSource(strings = { "00A0", "2007", "202F", "3000", "2028", "2029" })
	void shouldRejectHubNameMadeOnlyOfSeparators(String codePoint) {
		var separator = Character.toString(Integer.parseInt(codePoint, 16));

		assertRejectsField(
			() -> Hub.register(separator + " " + separator, HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10),
			"name");
	}

	@Requirement("RIA-56")
	@ParameterizedTest(name = "U+{0}")
	@ValueSource(strings = { "00A0", "2007", "202F", "3000", "2028", "2029" })
	void shouldStoreHubNameWithoutSurroundingSeparators(String codePoint) {
		var separator = Character.toString(Integer.parseInt(codePoint, 16));

		var hub = Hub.register(separator + NAME + " " + separator, HubKind.HOTEL, MARES_PRAIA, LATITUDE,
			LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo(NAME);
	}

	@Requirement("RIA-56")
	@Test
	void shouldAcceptHubNameOf120CharactersSurroundedByNoBreakSpaces() {
		var name = "A".repeat(120);

		var hub = Hub.register("\u00A0" + name + "\u00A0", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo(name);
	}

	@Requirement("RIA-56")
	@Test
	void shouldKeepNoBreakSpaceInsideHubName() {
		var hub = Hub.register("Marés\u00A0Praia", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10);

		assertThat(hub.getName()).isEqualTo("Marés\u00A0Praia");
	}

	// Name: no invisible characters, that is, no C0 or C1 control character (Unicode category Cc)
	// and no format character (category Cf), such as the right-to-left override U+202E.

	@Requirement("RIA-56")
	@ParameterizedTest(name = "U+{0}")
	@ValueSource(strings = { "0000", "001F", "007F", "0080", "0085", "009F", "200B", "200D", "202E", "2066", "FEFF" })
	void shouldRejectHubNameWithControlOrFormatCharacter(String codePoint) {
		var character = Character.toString(Integer.parseInt(codePoint, 16));

		assertRejectsField(() -> Hub.register("Marés" + character + "Praia", HubKind.HOTEL, MARES_PRAIA, LATITUDE,
			LONGITUDE, 10), "name");
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectHubNameWithNextLineCharacter() {
		assertRejectsField(
			() -> Hub.register("Marés Praia\u0085Beach Front", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10),
			"name");
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectHubNameWithRightToLeftOverride() {
		assertRejectsField(
			() -> Hub.register("Marés Praia‮tnorF hcaeB", HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10),
			"name");
	}

	// Visible letters, accents and punctuation outside ASCII stay valid.
	@Requirement("RIA-56")
	@Test
	void shouldAcceptHubNameWithAccentsAndDashes() {
		var name = "Marés Salinas – Jardim das Gaivotas · Ponte Nova";

		assertThat(Hub.register(name, HubKind.HOTEL, MARES_PRAIA, LATITUDE, LONGITUDE, 10).getName()).isEqualTo(name);
	}

	// Kind and hotel: a hotel hub belongs to a hotel; a tourist spot hub to none.

	@Requirement("RIA-56")
	@Test
	void shouldRejectHubWithoutKind() {
		assertRejectsField(() -> Hub.register(NAME, null, MARES_PRAIA, LATITUDE, LONGITUDE, 10), "kind");
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectHotelHubWithoutHotel() {
		assertRejectsField(() -> Hub.register(NAME, HubKind.HOTEL, null, LATITUDE, LONGITUDE, 10), "hotel");
	}

	@Requirement("RIA-56")
	@Test
	void shouldRejectTouristSpotHubWithHotel() {
		assertRejectsField(
			() -> Hub.register("Miradouro das Salinas", HubKind.TOURIST_SPOT, MARES_PRAIA, 40.6425, -8.7479, 6), "hotel");
	}

	// Position: latitude from -90 to 90 and longitude from -180 to 180 decimal degrees, inclusive.

	@Requirement("RIA-56")
	@ParameterizedTest
	@ValueSource(doubles = { -90.0, 0.0, 90.0 })
	void shouldAcceptLatitudeFromMinus90To90(double latitude) {
		assertThat(hotelHubAt(latitude, LONGITUDE).getLatitude()).isEqualTo(latitude);
	}

	@Requirement("RIA-56")
	@ParameterizedTest
	@ValueSource(doubles = { -90.0001, 90.0001, -180.0, 180.0, Double.NaN,
		Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY })
	void shouldRejectLatitudeOutsideMinus90To90(double latitude) {
		assertRejectsField(() -> hotelHubAt(latitude, LONGITUDE), "latitude");
	}

	@Requirement("RIA-56")
	@ParameterizedTest
	@ValueSource(doubles = { -180.0, 0.0, 180.0 })
	void shouldAcceptLongitudeFromMinus180To180(double longitude) {
		assertThat(hotelHubAt(LATITUDE, longitude).getLongitude()).isEqualTo(longitude);
	}

	@Requirement("RIA-56")
	@ParameterizedTest
	@ValueSource(doubles = { -180.0001, 180.0001, -360.0, 360.0, Double.NaN,
		Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY })
	void shouldRejectLongitudeOutsideMinus180To180(double longitude) {
		assertRejectsField(() -> hotelHubAt(LATITUDE, longitude), "longitude");
	}

	@Requirement("RIA-56")
	@Test
	void shouldExplainRejectionWithoutEchoingHubName() {
		assertThatThrownBy(() -> hotelHubWithCapacity(0))
			.isInstanceOf(InvalidHubException.class)
			.hasMessageNotContaining(NAME)
			.message().isNotBlank();
	}

}
