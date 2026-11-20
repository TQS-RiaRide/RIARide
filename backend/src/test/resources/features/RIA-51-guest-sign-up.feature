@RIA-51
Feature: Guest sign-up with a stay code
  As a guest, I want to create an account with my email, a password and my stay code,
  so that I can buy a pass and use the bikes.

  The acceptance tests run with today fixed at 30 November 2026 in Vila Ria.

  @TEST_RIA-148 @BR1
  Scenario: A guest with a stay code for an active stay signs up
    Given a stay at "Marés Centro" for guest "Rita Almeida" from "2026-11-28" to "2026-12-02"
    And today is "2026-11-30"
    When a guest signs up with email "rita.almeida@example.test", password "Sunny-Ria-2026" and that stay code
    Then the response status is 201
    And an account exists for "rita.almeida@example.test" linked to that stay

  @BR1
  Scenario: A guest with a stay code for an ended stay cannot sign up
    Given a stay at "Marés Centro" for guest "Rita Almeida" from "2026-11-25" to "2026-11-29"
    And today is "2026-11-30"
    When a guest signs up with email "rita.almeida@example.test", password "Sunny-Ria-2026" and that stay code
    Then the response is a problem detail with status 422
    And the problem detail says the stay has already ended
    And no account exists for "rita.almeida@example.test"
