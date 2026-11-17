@RIA-52
Feature: Buy a pass
  As a guest, I want to buy a pass and pay the deposit,
  so that I can make trips during my stay.

  Background:
    Given the base price of a stay pass is 25.00 EUR and the deposit is 50.00 EUR
    And a guest "Rita Almeida" with an active stay at "Marés Salinas" is logged in

  @BR2 @BR3 @BR4 @BR5 @wip
  Scenario: A guest buys a stay pass
    Given the payment sandbox accepts the card
    When the guest buys a "stay" pass
    Then the response status is 201
    And the payment sandbox charged 25.00 EUR for the pass
    And the payment sandbox charged a deposit of 50.00 EUR
    And the guest has a "stay" pass that is not active yet

  @BR5 @wip
  Scenario: No pass is created when the card is declined
    Given the payment sandbox declines the card
    When the guest buys a "stay" pass
    Then the response is a problem detail with status 402
    And the guest has no pass
