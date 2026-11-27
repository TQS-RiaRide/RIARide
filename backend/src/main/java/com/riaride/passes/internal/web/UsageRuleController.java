package com.riaride.passes.internal.web;

import com.riaride.passes.internal.application.UsageRuleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The rules of use of the hotel group (RIA-96), one set for every hotel, as the base price is
// (BR3). SecurityConfiguration also restricts /api/v1/usage-rules to these roles.
@RestController
@RequestMapping("/api/v1/usage-rules")
@PreAuthorize("hasAnyRole('HOTEL_MANAGER', 'ADMIN')")
class UsageRuleController {

  private final UsageRuleService rules;

  UsageRuleController(UsageRuleService rules) {
    this.rules = rules;
  }

  @GetMapping
  UsageRuleResponse current() {
    return UsageRuleResponse.from(rules.current());
  }

  // The change applies to trips that start from now on; a trip in progress keeps its rule.
  @PutMapping
  UsageRuleResponse change(@Valid @RequestBody UsageRuleRequest request) {
    return UsageRuleResponse.from(rules.change(request.maxTripDurationMinutes()));
  }
}
