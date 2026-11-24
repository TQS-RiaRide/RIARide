"""Minimal fleet telemetry simulator for RiaRide (ADR 0006, ADR 0007).

The simulator stands in for the e-bikes. It publishes telemetry and fault
events over MQTT and reacts to UNLOCK and LOCK commands from the platform.
The message formats are the JSON Schemas in contracts/mqtt/ at the
repository root.
"""
