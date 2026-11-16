# 2.2 Personas and scenarios

> Draft for section 2.2 of the Product Specification report (RIA-8). There is one persona for each main actor (guest, operator, hotel manager) and one scenario for each persona.

The technician and the admin have no persona of their own. The technician appears in Rui's scenario, and the admin only manages accounts and roles.

## Sophie Laurent, the guest

- **Age and occupation:** 34, designer, from Lyon (France).
- **Context:** staying 4 nights at the Marés Praia hotel with her boyfriend. She speaks French and English, but not Portuguese.
- **Goals:** explore Vila Ria by bike at her own pace; always know how much she will pay.
- **Frustrations:** apps that ask for documents or local phone numbers; hidden costs; bikes with a flat battery.

## Rui Matos, the fleet operator

- **Age and occupation:** 41, operations manager at RodaLeve Fleet Services.
- **Context:** manages the bikes at every hub and coordinates two technicians.
- **Goals:** have working bikes at every hub; repair faults within the SLA deadline; move bikes between hubs before the busiest hours.
- **Frustrations:** only learning about a fault when a guest complains; hubs that are full at one hotel and empty at another.

## Ana Ribeiro, the hotel manager

- **Age and occupation:** 45, guest experience manager at Grupo Hoteleiro Marés.
- **Context:** manages the bike service at the group's three hotels.
- **Goals:** offer the bikes as a service that guests value; set the base price of the passes, the same for the group's three hotels; check that the operator meets the SLA, the service level agreement signed with the group.
- **Frustrations:** paying for a service without knowing how much it is used or how often the bikes are unavailable.

## Scenarios

### Scenario 1: Sophie rides to the salt pans

Sophie checks in at Marés Praia and gets a stay code at the front desk. On her phone, she switches RiaRide to English and creates an account with her email, a password and the stay code. She chooses the stay pass instead of the 1-day or 3-day pass, and the deposit is charged to her card.

The next morning, she opens the suggested route "Marés Praia → Historic centre → Salinas → Marés Praia" and sees that there are three bikes at the hotel hub. When she starts her first trip, the pass becomes active. She rides to the historic centre and returns the bike at a hub with a free slot. After lunch, she picks up another bike and rides on to the salt pans.

On her last day, she returns the bike before check-out. The pass ends at check-out and the deposit is refunded. On the dashboard, Sophie sees all her trips and the total she paid, with no extra costs.

### Scenario 2: Rui fixes a fault before the rush hour

At 8:00, Rui opens the operator dashboard. He sees that a bike at the Historic centre hub reported a brake fault overnight. From the bikes' positions, the dashboard also shows that the Marés Centro hub is full and the Salinas hub is almost empty.

Overnight, the fault automatically opened a maintenance ticket with an SLA deadline, and the bike changed to maintenance, so that no guest can book it. Rui assigns the ticket to a technician. Then he arranges with the team to take four bikes from Marés Centro to Salinas in a van. This task is done outside RiaRide.

In the afternoon, the technician repairs the brakes and closes the ticket. The bike becomes available again.

### Scenario 3: Ana prepares the summer season

Before the summer, Ana logs in to the hotel backoffice. She updates the base price of the 1-day pass, the 3-day pass and the stay pass, which applies to the group's three hotels. She also increases the capacity of the Marés Praia hub, which was often full the year before.

Then she checks last month's indicators: bike utilization, availability against the SLA, trips per day and average time to repair. She notices that availability at Marés Salinas fell below the SLA for two weeks and sets up a meeting with the operator to discuss it.
