package com.riaride.accounts.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A guest's stay at a hotel of the group, registered at check-in.
@Entity
@Table(name = "stay")
public class Stay implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "hotel_id", nullable = false, updatable = false)
	private UUID hotelId;

	@Column(name = "guest_name", nullable = false)
	private String guestName;

	@Column(name = "stay_code", nullable = false, updatable = false, length = StayCode.LENGTH)
	private StayCode stayCode;

	@Column(name = "check_in", nullable = false)
	private LocalDate checkIn;

	@Column(name = "check_out", nullable = false)
	private LocalDate checkOut;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StayStatus status;

	// The id is assigned in the domain, so JPA cannot tell a new stay from an existing one.
	@Transient
	private boolean isNew;

	protected Stay() {
		// for JPA
	}

	private Stay(UUID hotelId, String guestName, StayCode stayCode, LocalDate checkIn, LocalDate checkOut) {
		this.id = UUID.randomUUID();
		this.hotelId = hotelId;
		this.guestName = guestName;
		this.stayCode = stayCode;
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		this.status = StayStatus.ACTIVE;
		this.isNew = true;
	}

	// today is the current date at the hotel. A stay may be registered late, after check-in,
	// but not once it has ended; on the check-out day it is still active.
	public static Stay register(UUID hotelId, String guestName, StayCode stayCode, LocalDate checkIn,
		LocalDate checkOut, LocalDate today) {
		if (!checkOut.isAfter(checkIn)) {
			throw new InvalidStayDatesException();
		}
		if (today.isAfter(checkOut)) {
			throw new StayAlreadyEndedException();
		}
		return new Stay(hotelId, guestName, stayCode, checkIn, checkOut);
	}

	// BR1: the stay code can be used to sign up until the end of the check-out day.
	public boolean hasEndedOn(LocalDate today) {
		return today.isAfter(checkOut);
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
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
