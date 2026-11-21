package com.riaride.accounts.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

// A guest's account: the Keycloak user, linked to the stay whose code they signed up with.
// Credentials live in Keycloak only; this table never holds a password.
@Entity
@Table(name = "guest")
public class Guest implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "keycloak_id", nullable = false, updatable = false)
	private String keycloakId;

	@Column(name = "stay_id", nullable = false, updatable = false)
	private UUID stayId;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String locale;

	// The id is assigned in the domain, so JPA cannot tell a new guest from an existing one.
	@Transient
	private boolean isNew;

	protected Guest() {
		// for JPA
	}

	private Guest(String keycloakId, UUID stayId, String email, String locale) {
		this.id = UUID.randomUUID();
		this.keycloakId = keycloakId;
		this.stayId = stayId;
		this.email = email;
		this.locale = locale;
		this.isNew = true;
	}

	public static Guest signUp(String keycloakId, UUID stayId, String email, String locale) {
		return new Guest(keycloakId, stayId, email, locale);
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

	public String getKeycloakId() {
		return keycloakId;
	}

	public UUID getStayId() {
		return stayId;
	}

	public String getEmail() {
		return email;
	}

	public String getLocale() {
		return locale;
	}

}
