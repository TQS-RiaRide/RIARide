package com.riaride.fleet.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the straight-line distance between two hubs, which a full hub uses to
// suggest the nearest ones with a free slot (RIA-55, architecture section 7). The expected values
// come from the haversine formula on a sphere of radius 6371 km.
class HubDistanceTest {

  private static Hub hub(String name, double latitude, double longitude) {
    return Hub.register(name, HubKind.TOURIST_SPOT, null, latitude, longitude, 10);
  }

  @Requirement("RIA-55")
  @Test
  void shouldMeasureTheDistanceBetweenTwoHubsInMetres() {
    var praia = hub("Praia", 40.6405, -8.7538);
    var salinas = hub("Salinas", 40.6510, -8.6820);
    var centro = hub("Centro", 40.6443, -8.6455);

    assertThat(praia.metersTo(salinas)).isCloseTo(6169.2, within(0.5));
    assertThat(praia.metersTo(centro)).isCloseTo(9147.4, within(0.5));
  }

  @Requirement("RIA-55")
  @Test
  void shouldBeTheSameDistanceBothWays() {
    var praia = hub("Praia", 40.6405, -8.7538);
    var salinas = hub("Salinas", 40.6510, -8.6820);

    assertThat(salinas.metersTo(praia)).isCloseTo(praia.metersTo(salinas), within(1e-6));
  }

  @Requirement("RIA-55")
  @Test
  void shouldBeZeroForTheSamePosition() {
    var praia = hub("Praia", 40.6405, -8.7538);
    var samePlace = hub("Praia Norte", 40.6405, -8.7538);

    assertThat(praia.metersTo(samePlace)).isZero();
  }

  // One degree of longitude on the equator is 1/360 of the sphere's circumference.
  @Requirement("RIA-55")
  @Test
  void shouldMeasureOneDegreeOnTheEquator() {
    var west = hub("West", 0, 0);
    var east = hub("East", 0, 1);

    assertThat(west.metersTo(east)).isCloseTo(111_194.9, within(0.5));
  }
}
