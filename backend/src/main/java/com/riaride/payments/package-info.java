// Payments behind the PaymentGateway port (ADR 0008): the purchase of a pass with its deposit,
// off-session overtime charges and refunds. Only this package is public; the Stripe adapter
// under internal is the module's own, so replacing the provider changes no other module.
@org.springframework.modulith.ApplicationModule(displayName = "Payments")
package com.riaride.payments;
