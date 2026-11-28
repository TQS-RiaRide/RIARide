# Spec Delta

## Purpose

Lets RodaLeve's operators share the repair work by giving each open maintenance ticket to one technician, and lets each technician see the tickets that are theirs to repair.

## ADDED Requirements

### Requirement: Operator assigns an open ticket to a technician
The system SHALL let a user with the operator or admin role assign an open maintenance ticket to a technician, given the ticket and the technician's user id. The ticket SHALL then be assigned to that technician, with the time of the assignment, and keep the status `OPEN`. The response SHALL contain the ticket with its assigned technician and assignment time.

#### Scenario: Operator assigns an open ticket
- **GIVEN** an open ticket for bike "EB-501" that is assigned to nobody
- **AND** a user "Bruno Costa" with the technician role
- **WHEN** an operator assigns the ticket to "Bruno Costa"
- **THEN** the response status is 200
- **AND** the ticket is assigned to "Bruno Costa", at the time of the request
- **AND** the ticket is still `OPEN`

#### Scenario: Admin assigns an open ticket
- **GIVEN** an open ticket assigned to nobody and a user with the technician role
- **WHEN** an admin assigns the ticket to that user
- **THEN** the response status is 200
- **AND** the ticket is assigned to that user

### Requirement: Open ticket can be reassigned
The system SHALL let an operator or admin assign an open ticket that is already assigned to another technician. The new technician SHALL replace the previous one, with the time of the new assignment.

#### Scenario: Operator reassigns a ticket to another technician
- **GIVEN** an open ticket assigned to technician "Bruno Costa"
- **AND** a user "Carla Dias" with the technician role
- **WHEN** an operator assigns the ticket to "Carla Dias"
- **THEN** the response status is 200
- **AND** the ticket is assigned to "Carla Dias" and no longer to "Bruno Costa"

#### Scenario: Assigning a ticket to the technician it already has
- **GIVEN** an open ticket assigned to technician "Bruno Costa"
- **WHEN** an operator assigns the ticket to "Bruno Costa" again
- **THEN** the response status is 200
- **AND** the ticket is assigned to "Bruno Costa", with the time of this request

### Requirement: Only technicians receive tickets
The system SHALL assign a ticket only to an existing user of the platform who has the technician role. A user id that names no user, or a user without the technician role, SHALL be refused with status 422, a problem detail that names the `technicianId` field, and no change to the ticket.

#### Scenario: Assigning to a user who is not a technician
- **GIVEN** an open ticket assigned to nobody
- **AND** a user with the operator role and no technician role
- **WHEN** an operator assigns the ticket to that user
- **THEN** the response is a problem detail with status 422 that names the field `technicianId`
- **AND** the ticket is still assigned to nobody

#### Scenario: Assigning to a user who does not exist
- **GIVEN** an open ticket assigned to nobody
- **WHEN** an operator assigns the ticket to a user id that names no user
- **THEN** the response is a problem detail with status 422 that names the field `technicianId`
- **AND** the ticket is still assigned to nobody

### Requirement: Only an open ticket can be assigned
The system SHALL refuse to assign a ticket that is not open, with status 409 and a problem detail, and leave the ticket unchanged.

#### Scenario: Assigning a ticket that is no longer open
- **GIVEN** a ticket that is no longer open
- **WHEN** an operator assigns it to a technician
- **THEN** the response is a problem detail with status 409
- **AND** the ticket's technician does not change

### Requirement: Assignment request is checked
The system SHALL answer 404 with a problem detail when the ticket does not exist, and 400 with a problem detail that names the field when the technician's user id is missing or is not a user id. A problem detail SHALL NOT repeat the submitted values.

#### Scenario: Ticket does not exist
- **WHEN** an operator assigns a ticket id that names no ticket to a technician
- **THEN** the response is a problem detail with status 404

#### Scenario: Technician id is missing or malformed
- **GIVEN** an open ticket
- **WHEN** an operator sends an assignment with no technician id, or with one that is not a user id
- **THEN** the response is a problem detail with status 400 that names the field `technicianId`
- **AND** the ticket does not change

### Requirement: Assignment needs the operator or admin role
The system SHALL refuse an assignment from an unauthenticated caller with status 401, and from a user with only the guest, technician or hotel manager role with status 403, before the request body is read, and leave the ticket unchanged.

#### Scenario: A technician tries to assign a ticket
- **GIVEN** an open ticket
- **WHEN** a user with only the technician role assigns it
- **THEN** the response status is 403
- **AND** the ticket does not change

#### Scenario: An unauthenticated caller tries to assign a ticket
- **WHEN** a caller without a token assigns a ticket
- **THEN** the response status is 401

### Requirement: Assignment fails safely when the identity provider is down
The system SHALL answer 503 with a problem detail, and leave the ticket unchanged, when it cannot check the technician because the identity provider cannot be reached or answers with an error.

#### Scenario: Identity provider unavailable
- **GIVEN** an open ticket assigned to nobody
- **AND** the identity provider cannot be reached
- **WHEN** an operator assigns the ticket to a technician
- **THEN** the response is a problem detail with status 503
- **AND** the ticket is still assigned to nobody

### Requirement: Technician lists their assigned tickets
The system SHALL let a user with the technician role list the open tickets assigned to them, earliest SLA deadline first. Each item SHALL give the ticket id, the bike's code, the status, the description, the opening time and the SLA deadline. Tickets assigned to other technicians, or to nobody, SHALL NOT be listed.

#### Scenario: Assigned ticket appears in the technician's list
- **GIVEN** an open ticket for bike "EB-501" that is assigned to nobody
- **AND** a user "Bruno Costa" with the technician role
- **WHEN** an operator assigns the ticket to "Bruno Costa"
- **AND** "Bruno Costa" lists the tickets assigned to them
- **THEN** the list contains the ticket for bike "EB-501"

#### Scenario: Tickets are listed by deadline, and only the technician's own
- **GIVEN** technician "Bruno Costa" has two open tickets, due at 12:00 and at 10:00
- **AND** technician "Carla Dias" has one open ticket
- **AND** one open ticket is assigned to nobody
- **WHEN** "Bruno Costa" lists the tickets assigned to them
- **THEN** the list contains exactly Bruno's two tickets, the one due at 10:00 first

#### Scenario: Technician with no tickets
- **GIVEN** a technician with no ticket assigned
- **WHEN** they list the tickets assigned to them
- **THEN** the response status is 200 and the list is empty

#### Scenario: A reassigned ticket leaves the previous technician's list
- **GIVEN** an open ticket assigned to "Bruno Costa"
- **WHEN** an operator reassigns it to "Carla Dias"
- **THEN** the ticket is no longer in Bruno's list
- **AND** it is in Carla's list

### Requirement: Only technicians list assigned tickets
The system SHALL refuse the list of assigned tickets to an unauthenticated caller with status 401, and to a user without the technician role with status 403.

#### Scenario: An operator asks for the technician's list
- **WHEN** a user with only the operator role lists the tickets assigned to them
- **THEN** the response status is 403
