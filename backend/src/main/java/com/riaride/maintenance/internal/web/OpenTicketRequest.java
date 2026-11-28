package com.riaride.maintenance.internal.web;

// The bike's code (for example EB-701, in any case) and what is wrong with it.
record OpenTicketRequest(String vehicleCode, String description) {}
