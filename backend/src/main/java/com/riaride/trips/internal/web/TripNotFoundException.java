package com.riaride.trips.internal.web;

// No trip with this id belongs to the caller. Another guest's trip is reported the same way, so
// its existence is not revealed.
class TripNotFoundException extends RuntimeException {

	TripNotFoundException() {
		super("You have no trip with this id.");
	}

}
