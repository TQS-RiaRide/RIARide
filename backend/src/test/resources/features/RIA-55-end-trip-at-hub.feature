@RIA-55
Feature: End a trip at a hub
  As a guest, I want to end my trip at any hub with a free slot,
  so that I can return the bike where it suits me.

  Background:
    Given a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a trip in progress with bike "EB-301"

  @TEST_RIA-152 @BR10 @wip
  Scenario: The guest ends the trip at a hub with a free slot
    Given a hub "Marés Praia" with capacity 10 and 9 bikes docked
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the trip is "ended"
    And bike "EB-301" is docked at hub "Marés Praia"

  # Nearest is the straight-line distance from the full hub's position (architecture, section 7).
  @BR10 @wip
  Scenario: The guest cannot end the trip at a full hub and nearby hubs are suggested
    Given these hubs:
      | name          | latitude | longitude | capacity | docked |
      | Marés Praia   | 40.6405  | -8.7538   | 10       | 10     |
      | Marés Salinas | 40.6510  | -8.6820   | 8        | 3      |
      | Marés Centro  | 40.6443  | -8.6455   | 12       | 6      |
    When the guest ends the trip at hub "Marés Praia"
    Then the response is a problem detail with status 409
    And the trip is "in progress"
    And the problem detail suggests the hubs "Marés Salinas, Marés Centro" in that order
