package com.riaride.accounts;

import java.util.List;
import java.util.Optional;

// Public API of the accounts module: looks up the hotels of the group, so other modules can
// refer to a hotel without reaching into accounts' tables (architecture, section 5).
public interface HotelDirectory {

  // Exact match on the hotel's name; empty when no hotel of the group has it.
  Optional<HotelSummary> findByName(String name);

  // Every hotel of the group, by name (RIA-107: the hub list names each hub's hotel).
  List<HotelSummary> findAll();
}
