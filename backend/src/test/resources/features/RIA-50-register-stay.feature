@RIA-50
Feature: Register a stay
  As a hotel manager, I want to register a guest's stay at check-in,
  so that the guest receives a stay code to create an account.

  @TEST_RIA-147 @BR1
  Scenario: The hotel manager registers a stay and a unique stay code is generated
    Given the hotel manager of "Marés Praia" is logged in
    When they register a stay at "Marés Praia" for guest "Rita Almeida" from "2026-12-01" to "2026-12-05"
    Then the response status is 201
    And the stay is registered with check-in "2026-12-01" and check-out "2026-12-05"
    And the response contains a stay code
    And the stay code is different from every other registered stay code

  @BR1
  Scenario: A user with the guest role cannot register a stay
    Given a guest "Tomás Ferreira" is logged in
    When they register a stay at "Marés Praia" for guest "Rita Almeida" from "2026-12-01" to "2026-12-05"
    Then the response is a problem detail with status 403
    And no stay is registered for guest "Rita Almeida"
