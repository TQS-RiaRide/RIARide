# 2.3 Project roadmap and priorities

> Draft for section 2.3 of the Product Specification report (RIA-9). The user stories are in the [Jira backlog](https://riaride.atlassian.net/jira/software/projects/RIA/boards/1); this section lists them by iteration.

The stories are organized by iteration, from highest to lowest priority. I2 holds the *Highest* stories, which make up the core flow. I3 holds the *High* stories, and I4 the rest, *High* and *Medium*. I5 adds no new stories.

Each story follows the format **As a** \<role\>, **I want** \<action\> **so that** \<benefit\>. The acceptance criteria follow the format **Given** \<context\>, **when** \<action\>, **then** \<result\>, so that they can be turned directly into Gherkin scenarios automated with Cucumber.

## I2 (17–24 Nov): core flow

Also in this iteration: the minimal simulator and its integration, CI with a blocking quality gate, Xray set up, SLOs defined and QA Manual v1. The first agent-generated story (AI4) is 6.1.

| # | User story | Acceptance criteria | Priority |
|---|---|---|---|
| 1.1 | **Register a stay.** As a hotel manager, I want to register a guest's stay so that the guest receives a stay code to use RiaRide. | Given a guest checking in at Marés Praia, when the hotel manager registers the stay with the check-in and check-out dates, then a unique stay code is generated. | Highest |
| 1.2 | **Guest sign-up with a stay code.** As a guest, I want to create an account with an email, a password and a stay code so that I can use the bikes during my stay. | • Given a valid code of an active stay, when the guest signs up, then the account is created.<br>• Given a code of a stay that has already ended, when the guest tries to sign up, then the sign-up is refused with a clear message. | Highest |
| 2.1 | **Buy a pass.** As a guest, I want to buy a 1-day, 3-day or stay pass so that I can ride bikes during my stay. | Given an authenticated guest with an active stay, when they buy a stay pass, then the price and the deposit are charged in the payment sandbox and the pass is created, but not yet active. | Highest |
| 3.1 | **See available bikes.** As a guest, I want to see the available bikes by hub and type so that I know where to pick one up. | Given a hub with 3 available bikes and 1 with 15 % battery, when the guest views that hub, then only the 3 available bikes are shown as "can be booked". | Highest |
| 3.2 | **Start a trip now.** As a guest, I want to start a trip with an available bike so that I can leave right away. | • Given a guest with a valid pass and no trip in progress, when they start a trip, then the bike is reserved, the trip is starting and the unlock command is sent.<br>• Given a starting trip, when telemetry confirms the unlock, then the trip starts and the bike changes to in use.<br>• Given a starting trip, when the unlock is not confirmed within 30 seconds, then the trip fails, the pass stays inactive and the bike stays reserved until the reservation expires.<br>• Given a guest with a trip in progress, when they try to start another one, then the request is refused. | Highest |
| 3.4 | **End the trip at a hub.** As a guest, I want to end the trip at any hub with a free slot so that I don't have to go back to where I started. | Given a hub at full capacity, when the guest tries to end the trip at that hub, then the request is refused and the nearest hubs with free slots are suggested. | Highest |
| 6.1 | **Register a hub.** As an operator, I want to register hubs with their capacity so that I know where the bikes are kept. | Given a new hub at Marés Praia with capacity for 10 bikes, when the operator registers it, then the hub appears in the fleet with 10 slots. | Highest |
| 6.2 | **Register a bike.** As an operator, I want to register bikes at a hub so that they become available to guests. | Given a hub with free slots, when the operator registers a bike at that hub, then the bike becomes available. | Highest |
| 9.1 | **Receive telemetry from the simulator.** As an operator, I want the system to receive the battery, position and status of each bike so that the fleet is kept up to date. | Given the simulator sending data over MQTT, when a reading for a bike arrives, then that bike's status is updated in less than 5 seconds. | Highest |
| 9.2 | **Block bikes with low battery.** As a guest, I want to be able to book only bikes with enough battery so that I don't get stuck halfway. | Given an available bike, when telemetry reports 18 % battery, then the bike can no longer be booked. | Highest |

## I3 (24 Nov–1 Dec): time rules, maintenance and complete API

Also in this iteration: continuous delivery (CD), persistence and integration tests. By the end, the REST API must be complete. The second agent-generated story (AI4) is 7.2.

| # | User story | Acceptance criteria | Priority |
|---|---|---|---|
| 1.3 | **Login and roles.** As a user, I want to log in and see only the features of my role so that I only access what I need. | Given a user with the operator role, when they log in, then they see the operator dashboard and not the hotel backoffice. | High |
| 2.2 | **Pass activation on the first trip.** As a guest, I want the pass to start only on my first trip so that I don't lose time I haven't used yet. | Given a guest with a 1-day pass never used, when the unlock of the first trip is confirmed, then the pass becomes active and expires 24 hours later. | High |
| 2.3 | **Overtime charge.** As a hotel manager, I want long trips to be charged as overtime so that the bikes return to the hubs. | Given a trip that lasted 3 h 20 min, when it ends, then the guest pays 2 blocks of 15 minutes of overtime. | High |
| 2.5 | **Pass ends during a trip.** As a hotel manager, I want trip time after the pass ends to be charged as overtime so that guests don't use the bikes without a pass. | • Given a 1-day pass that expires at 15:00 and a trip in progress, when the trip ends at 15:30, then the guest pays 2 blocks of 15 minutes of overtime.<br>• Given a stay pass with check-out at 12:00 and a bike in use, when the trip ends at 12:20, then the guest pays 2 blocks of 15 minutes of overtime. | High |
| 3.3 | **Book a bike in advance.** As a guest, I want to book a bike in advance so that it is waiting for me when I arrive. | • Given an available bike, when the guest books it, then the bike changes to reserved.<br>• Given a reserved bike, when 15 minutes pass without the trip starting, then the reservation is cancelled and the bike changes back to available. | High |
| 6.3 | **Change a bike's status.** As an operator, I want to activate, deactivate or retire bikes so that I control which ones can be used. | • Given an available bike, when the operator retires it, then it changes to retired and is no longer shown to guests.<br>• Given a bike in use, when the operator tries to retire it, then the request is refused. | High |
| 7.1 | **Open a maintenance ticket.** As an operator, I want to open a ticket for a faulty bike so that it is repaired within the deadline. | Given an available bike with a fault, when the operator opens a ticket, then the bike changes to maintenance and the ticket gets an SLA deadline. | High |
| 7.2 | **Assign a ticket to a technician.** As an operator, I want to assign tickets to technicians so that the work is shared. | Given an open ticket, when the operator assigns it to a technician, then the ticket appears in that technician's list. | High |
| 7.3 | **Close a ticket.** As a technician, I want to close a ticket after the repair so that the bike returns to service. | Given a bike in maintenance with the ticket assigned to the technician, when the technician closes the ticket, then the bike changes back to available. | High |
| 7.4 | **Overdue deadline alert.** As an operator, I want to be warned when a ticket passes its deadline so that I meet the SLA. | Given an open ticket, when the SLA deadline passes without the ticket being closed, then the ticket is flagged as outside the SLA on the dashboard. | High |
| 8.2 | **Set the rules of use.** As a hotel manager, I want to set the maximum trip duration and the hub capacity so that I control the service. | • Given the maximum duration changed to 2 hours, when a trip goes over 2 hours, then overtime starts being charged.<br>• Given a hub with 8 bikes whose capacity changes to 8, when a guest tries to end a trip there, then the request is refused. | High |
| 9.3 | **Automatic ticket for a fault reported by telemetry.** As an operator, I want a fault reported by a bike to open a maintenance ticket right away so that the repair starts without waiting for a guest to complain. | • Given an available bike, when the simulator reports a brake fault, then a ticket with an SLA deadline is opened and the bike changes to maintenance.<br>• Given a bike in use, when the simulator reports a fault, then a ticket is opened right away, the trip continues and the bike only changes to maintenance when the trip ends.<br>• Given a fault already reported with an open ticket, when the same message arrives again (same `messageId`), then no second ticket is opened. | High |

## I4 (1–8 Dec): complete MVP and system tests

Focus of this iteration: representative data, end-to-end tests (Playwright or Selenium), a k6 load test against the SLOs and valued practices. The stories are mostly screens and dashboards that read data that already exists, and they are what the end-to-end tests will check.

| # | User story | Acceptance criteria | Priority |
|---|---|---|---|
| 1.4 | **Manage accounts and roles.** As an admin, I want to manage user accounts and roles so that I control who can access what. | • Given a user without the technician role, when the admin assigns them that role, then the user can see the ticket list.<br>• Given an account deactivated by the admin, when the user tries to log in, then the login is refused. | High |
| 2.4 | **Deposit refund.** As a guest, I want to get the deposit back at the end of the pass so that I only pay for what I used. | Given a pass that ended with no trips in progress and no reported faults, when the pass ends, then the deposit is refunded in the payment sandbox. | Medium |
| 4.1 | **Create a suggested route.** As a hotel manager, I want to create routes with an ordered list of hubs so that I can suggest rides to guests. | • Given a route that starts and ends at a hotel, when the manager saves it, then the route is published.<br>• Given a route that does not start and end at a hotel, when the manager tries to save it, then the request is refused. | Medium |
| 4.2 | **View a route.** As a guest, I want to see the suggested routes and the bikes available at the first hub so that I can plan my ride. | Given a published route, when the guest opens it, then they see the hubs in order and the number of bikes available at the first hub. | Medium |
| 5.1 | **See my pass.** As a guest, I want to see the type and validity of my pass so that I know until when I can use the bikes. | Given an active stay pass, when the guest opens the dashboard, then they see that the pass ends at the check-out date and time. | High |
| 5.2 | **See trips and costs.** As a guest, I want to see my trips and what I paid so that there are no surprises. | Given a guest with two trips, one of them with overtime, when they open the history, then they see both trips and the overtime amount. | High |
| 5.3 | **Change the language.** As a foreign guest, I want to use RiaRide in English so that I understand all the information. | Given a guest on the home page, when they choose English, then all text is shown in English. | Medium |
| 6.4 | **Fleet dashboard.** As an operator, I want to see the fleet status and the open tickets so that I know where bikes are missing and what needs repairing. | Given a full hub, an almost empty one and two open tickets, when the operator opens the dashboard, then they see the occupancy of each hub and the two tickets. | High |
| 8.1 | **Set the base price of the passes.** As a hotel manager, I want to set the base price of each pass type so that it is the same in the three hotels. | • Given a new price for the 1-day pass, when the manager saves it, then new purchases in the three hotels use that price.<br>• Given a pass already bought, when the price changes, then the pass keeps the old price. | High |
| 8.3 | **See the service KPIs.** As a hotel manager, I want to see utilization, availability against the SLA, trips per day and time to repair so that I can assess the operator. | Given last month's data, when the manager opens the KPIs, then they see the four indicators, per hotel. | High |
| 8.4 | **See the technical metrics.** As a hotel manager, I want to see API latency, error rate and telemetry lag so that I know whether the platform is working well. | Given the platform running, when the manager opens the technical metrics, then they see the three values, up to date. | High |

## I5 (8–15 Dec): stabilize and deliver

No new stories. The MVP must already be complete and in production at the start of this iteration. The work is:

- fix bugs;
- finish the Product Specification report, the QA Manual and the analysis of AI use (AI5);
- prepare the presentation and the demo;
- create the `final_v1` tag by the day of the presentation.

If an I4 story is late, it is finished here, but no new stories are added.
