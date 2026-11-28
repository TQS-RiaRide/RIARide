package com.riaride.passes.internal.web;

import com.riaride.passes.internal.application.PassQueryService;
import com.riaride.passes.internal.application.PurchasePassCommand;
import com.riaride.passes.internal.application.PurchasePassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Buying a pass (RIA-52). The client sends an Idempotency-Key header per purchase attempt and
// repeats it on a retry, so a retried request returns the same pass instead of charging twice.
// The guest reads their current pass for the dashboard (RIA-107).
@RestController
@RequestMapping("/api/v1/passes")
class PassController {

  private final PurchasePassService purchasePass;
  private final PassQueryService passes;

  PassController(PurchasePassService purchasePass, PassQueryService passes) {
    this.purchasePass = purchasePass;
    this.passes = passes;
  }

  @GetMapping("/current")
  @PreAuthorize("hasRole('GUEST')")
  PassResponse current(JwtAuthenticationToken authentication) {
    return passes
        .currentPass(authentication.getName())
        .map(PassResponse::from)
        .orElseThrow(NoCurrentPassException::new);
  }

  @PostMapping
  @PreAuthorize("hasRole('GUEST')")
  ResponseEntity<PassResponse> purchase(
      // At most 200 characters: the key is stored scoped by guest (37 more) and sent to Stripe,
      // whose limit is 255.
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
      @Valid @RequestBody PurchasePassRequest request,
      JwtAuthenticationToken authentication) {
    var pass =
        purchasePass.purchase(
            new PurchasePassCommand(
                authentication.getName(),
                request.passType(),
                request.paymentMethod(),
                idempotencyKey));
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(pass.getId())
            .toUri();
    return ResponseEntity.created(location).body(PassResponse.from(pass));
  }
}
