@RIA-95 @wip
Feature: SLA deadline breach alert
  As an operator, I want to be warned when a ticket passes its deadline,
  so that we meet the SLA.

  The scenarios have no step definitions yet (@wip, excluded from CI); RIA-95 adds them. A ticket
  opened by RIA-92 is due 24 hours after it was opened. A scheduled job marks the open tickets
  whose deadline has been reached; the scenarios run it at once on the test clock, so none waits.
  A ticket is out of SLA from the exact moment of its deadline (Product Owner decision). The
  operator sees the mark on the tickets the dashboard lists (RIA-120).

  Background:
    Given a hub "Marés Centro" with these bikes:
      | code   | status    | battery |
      | EB-911 | available | 80      |
    And the operator has opened a ticket for bike "EB-911" with the fault "Display does not turn on"

  @TEST_RIA-186
  Scenario: A ticket still open after its 24-hour deadline is marked as out of SLA
    Given 24 hours and 1 minute have passed since the ticket for bike "EB-911" was opened
    When the job that checks the SLA deadlines runs
    Then the ticket for bike "EB-911" is marked as out of SLA

  Scenario: A ticket is out of SLA at the exact moment of its deadline
    Given exactly 24 hours have passed since the ticket for bike "EB-911" was opened
    When the job that checks the SLA deadlines runs
    Then the ticket for bike "EB-911" is marked as out of SLA

  Scenario: A ticket still within its deadline is not marked
    Given 23 hours and 59 minutes have passed since the ticket for bike "EB-911" was opened
    When the job that checks the SLA deadlines runs
    Then the ticket for bike "EB-911" is not marked as out of SLA

  Scenario: A ticket closed before its deadline is never marked
    Given the technician "Bruno Costa" has an account with the technician role
    And the ticket for bike "EB-911" is assigned to technician "Bruno Costa"
    And technician "Bruno Costa" has closed the ticket for bike "EB-911"
    And 25 hours have passed since the ticket for bike "EB-911" was opened
    When the job that checks the SLA deadlines runs
    Then the ticket for bike "EB-911" is not marked as out of SLA
