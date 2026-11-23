package com.riaride.fleet.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import com.riaride.accounts.HotelDirectory;
import com.riaride.fleet.internal.domain.HubKind;
import com.riaride.fleet.internal.domain.InvalidHubException;
import com.riaride.fleet.internal.persistence.HubRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Unit test (Surefire): the rule that a tourist spot hub belongs to no hotel is checked before the
// hotel name is looked up, so a tourist spot hub with any hotel name gets the tourist spot reason,
// and the hotel directory (the accounts module's public API) is not even queried.
@ExtendWith(MockitoExtension.class)
class RegisterHubServiceTest {

  private static final String TOURIST_SPOT_REASON = "must not belong to a hotel";

  @Mock private HubRepository hubs;

  @Mock private HotelDirectory hotels;

  private RegisterHubService service;

  @BeforeEach
  void setUp() {
    service = new RegisterHubService(hubs, hotels);
  }

  private static RegisterHubCommand touristSpotHubAtHotel(String hotelName) {
    return new RegisterHubCommand(
        "Miradouro das Salinas", HubKind.TOURIST_SPOT, hotelName, 40.6425, -8.7479, 6);
  }

  @Requirement("RIA-56")
  @ParameterizedTest
  @ValueSource(strings = {"Hotel Inexistente", "Marés Praia"})
  void shouldRejectTouristSpotHubWithHotelBeforeLookingUpTheHotel(String hotelName) {
    assertThatThrownBy(() -> service.register(touristSpotHubAtHotel(hotelName)))
        .isInstanceOfSatisfying(
            InvalidHubException.class,
            invalid -> {
              assertThat(invalid.getField()).isEqualTo("hotel");
              assertThat(invalid.getMessage())
                  .contains(TOURIST_SPOT_REASON)
                  .doesNotContain("No hotel of the group has this name");
            });

    verifyNoInteractions(hotels, hubs);
  }

  @Requirement("RIA-56")
  @Test
  void shouldRejectHotelHubWhoseHotelIsNotInTheGroup() {
    when(hotels.findByName("Hotel Inexistente")).thenReturn(Optional.empty());
    var command =
        new RegisterHubCommand(
            "Marés Praia – Beach Front", HubKind.HOTEL, "Hotel Inexistente", 40.6405, -8.7538, 10);

    assertThatThrownBy(() -> service.register(command))
        .isInstanceOfSatisfying(
            InvalidHubException.class,
            invalid -> {
              assertThat(invalid.getField()).isEqualTo("hotel");
              assertThat(invalid.getMessage()).contains("No hotel of the group has this name");
            });

    verifyNoInteractions(hubs);
  }
}
