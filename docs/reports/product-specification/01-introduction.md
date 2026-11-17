# 1. Introduction

> Draft for section 1 of the Product Specification report (RIA-16).

## 1.1 Overview of the project

RiaRide is a shared e-bike service for tourist mobility, built for the TQS group project as a *light-mobility fleet-as-a-service* platform. The project has two aims: a working MVP, and evidence that quality was built in from the start.

Our concretization is tourist mobility in **Vila Ria**, a fictional coastal city. The institutional client is **Grupo Hoteleiro Marés**, with three hotels: Marés Centro, Marés Praia and Marés Salinas. The fleet operator is **RodaLeve Fleet Services**. Hotel guests buy a pass, ride between hubs at the hotels and the main tourist spots, and can follow routes suggested by the hotel group. The web UI is available in Portuguese and English.

## 1.2 Known limitations

This is the iteration I1 draft. The following questions are still open with the Product Owner and are tracked in section 13 of the [architecture proposal](../../architecture/README.md#13-risks-and-open-questions):

- Booking a vehicle for a period: a reservation currently holds a vehicle for 15 min only.
- Restricting who may use which vehicles, as a rule of use.
- Problem reports by guests.
- The duration of the 3-day pass, whether 1-day and 3-day passes also end at check-out, the overtime grace period, the overtime price and the deposit amount.

This section will be reviewed at the end of the project to list features that were planned but not delivered.

## 1.3 References and resources

- TQS 2026 group project guidelines and report template.
- Spring Boot and [Spring Modulith](https://spring.io/projects/spring-modulith) reference documentation.
- [Eclipse Mosquitto](https://mosquitto.org/) and [paho-mqtt](https://eclipse.dev/paho/) for the telemetry integration.
- [Stripe test mode](https://docs.stripe.com/testing) for payments.
- [Keycloak](https://www.keycloak.org/) for authentication.
- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details for HTTP APIs.
- Grudin, J. and Pruitt, J. (2002). *Personas, participatory design and product development.*
