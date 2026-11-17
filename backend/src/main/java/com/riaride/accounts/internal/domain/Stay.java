package com.riaride.accounts.internal.domain;

import java.time.LocalDate;
import java.util.UUID;

// A guest's stay at a hotel of the group, registered at check-in.
public class Stay {

	private UUID id;
	private UUID hotelId;
	private String guestName;
	private StayCode stayCode;
	private LocalDate checkIn;
	private LocalDate checkOut;
	private StayStatus status;

	private Stay(UUID hotelId, String guestName, StayCode stayCode, LocalDate checkIn, LocalDate checkOut) {
		this.id = UUID.randomUUID();
		this.hotelId = hotelId;
		this.guestName = guestName;
		this.stayCode = stayCode;
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		this.status = StayStatus.ACTIVE;
	}

	public static Stay register(UUID hotelId, String guestName, StayCode stayCode, LocalDate checkIn,
		LocalDate checkOut) {
		if (!checkOut.isAfter(checkIn)) {
			throw new InvalidStayDatesException();
		}
		return new Stay(hotelId, guestName, stayCode, checkIn, checkOut);
	}

	public UUID getId() {
		return id;
	}

	public UUID getHotelId() {
		return hotelId;
	}

	public String getGuestName() {
		return guestName;
	}

	public StayCode getStayCode() {
		return stayCode;
	}

	public LocalDate getCheckIn() {
		return checkIn;
	}

	public LocalDate getCheckOut() {
		return checkOut;
	}

	public StayStatus getStatus() {
		return status;
	}

}
