@RIA-87
Feature: Pass activation on first trip
  As a hotel guest, I want my pass to start only on my first trip,
  so that I don't lose time I haven't used.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-201" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in

  @BR2 @BR4
  Scenario: The first confirmed unlock activates a 1-day pass for 24 hours
    Given the guest has a "1-day" pass that was never used
    And the guest has a "starting" trip with bike "EB-201"
    When telemetry reports that bike "EB-201" is unlocked
    Then the trip is "in progress"
    And the guest's pass is active
    And the guest's pass expires 24 hours after the unlock was confirmed
