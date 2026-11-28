@RIA-89
Feature: Pass ends during a trip
  As a hotel manager, I want trip time after the pass ends to be charged as overtime,
  so that guests don't ride without a pass.

  Background:
    Given a hub "Marés Centro" with an available bike "EB-401" with 80 % battery
    And a hub "Marés Praia" with capacity 10 and 9 bikes docked
    And a guest "Rita Almeida" with an active stay at "Marés Centro" is logged in

  # The trip continues after the pass expires (BR8); the pass closes only once the trip has ended.
  @TEST_RIA-182 @BR8
  Scenario: A 1-day pass expires during a trip, and the 30 minutes after it are charged as overtime
    Given the guest has an active "1-day" pass that expires at "30 November 2026, 15:00"
    And the guest's pass was bought with a card saved for off-session charges
    And the payment sandbox accepts off-session charges
    And it is "30 November 2026, 14:00" in Vila Ria
    And the guest has a trip in progress with bike "EB-401"
    And it is "30 November 2026, 15:30" in Vila Ria
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the guest is charged 2 blocks of 15 minutes of overtime, 4.00 EUR, off-session
    And the guest's pass is closed, with its deposit still held

  # A stay pass ends with the stay, at midnight after the check-out day (Product Owner decision,
  # RIA-87). The guest checks out on 2 December, so the pass ends at 00:00 on 3 December.
  @BR9
  Scenario: A stay pass ends at check-out during a trip, and the 20 minutes after it are charged as overtime
    Given the guest has a "stay" pass that was never used
    And the guest's pass was bought with a card saved for off-session charges
    And the payment sandbox accepts off-session charges
    And it is "2 December 2026, 23:30" in Vila Ria
    And the guest has a trip in progress with bike "EB-401"
    And it is "3 December 2026, 00:20" in Vila Ria
    When the guest ends the trip at hub "Marés Praia"
    Then the response status is 200
    And the guest is charged 2 blocks of 15 minutes of overtime, 4.00 EUR, off-session
    And the guest's pass is closed, with its deposit still held

  # A pass that ends with no trip in progress is closed by a scheduled job, so the guest can buy
  # another: a guest holds only one open pass at a time (Product Owner decision, RIA-52).
  @BR8
  Scenario: A 1-day pass that expires with no trip in progress is closed, and the guest can buy another
    Given the guest has an active "1-day" pass that expires at "30 November 2026, 15:00"
    And it is "30 November 2026, 15:01" in Vila Ria
    When the job that closes ended passes runs
    Then the guest's pass is closed, with its deposit still held
    Given the payment sandbox accepts the card
    When the guest buys a "stay" pass
    Then the response status is 201
