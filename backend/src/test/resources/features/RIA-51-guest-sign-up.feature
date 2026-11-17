@RIA-51
Feature: Guest sign-up with a stay code
  As a guest, I want to create an account with my email, a password and my stay code,
  so that I can buy a pass and use the bikes.

  @BR1 @wip
  Scenario: A guest with a stay code for an active stay signs up
    Given a stay at "Marés Centro" for guest "Rita Almeida" from "2026-12-01" to "2026-12-05"
    And today is "2026-12-02"
    When a guest signs up with email "rita.almeida@example.test", password "Sunny-Ria-2026" and that stay code
    Then the response status is 201
    And an account exists for "rita.almeida@example.test" linked to that stay

  @BR1 @wip
  Scenario: A guest with a stay code for an ended stay cannot sign up
    Given a stay at "Marés Centro" for guest "Rita Almeida" from "2026-12-01" to "2026-12-05"
    And today is "2026-12-06"
    When a guest signs up with email "rita.almeida@example.test", password "Sunny-Ria-2026" and that stay code
    Then the response is a problem detail with status 422
    And the problem detail says the stay has already ended
    And no account exists for "rita.almeida@example.test"
