package com.riaride.accounts.internal.application;

// The identity provider's user for a sign-up. created is false when an orphan user, left
// behind by an earlier sign-up that failed locally, was reused instead of a new one created.
public record GuestUser(String id, boolean created) {}
