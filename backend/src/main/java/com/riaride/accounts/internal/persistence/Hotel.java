package com.riaride.accounts.internal.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

// A hotel of the group. Reference data loaded by Flyway; read-only for now.
@Entity
@Table(name = "hotel")
public class Hotel {

	@Id
	private UUID id;

	private String name;

	protected Hotel() {
		// for JPA
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
