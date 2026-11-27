@RIA-90
Feature: Book a bike in advance
  As a guest, I want to book a bike in advance,
  so that it is waiting for me when I arrive.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-701" with 80 % battery
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in
    And the guest has a valid "stay" pass

  @BR11
  Scenario: Booking an available bike reserves it
    When the guest books bike "EB-701"
    Then the response status is 201
    And bike "EB-701" is "reserved"
    And the booking holds bike "EB-701" for 15 minutes

  @BR11
  Scenario: A booking not used within 15 minutes is cancelled and the bike is available again
    Given the guest booked bike "EB-701"
    When 15 minutes pass without a trip starting on bike "EB-701"
    Then the booking has expired
    And bike "EB-701" is "available"

  @BR11
  Scenario: A trip started on the booked bike uses the booking
    Given the guest booked bike "EB-701"
    When the guest starts a trip with bike "EB-701"
    Then the response status is 202
    And the trip uses the booking
