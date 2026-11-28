package com.riaride.accounts.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import org.junit.jupiter.api.Test;

// Unit test (Surefire): the sign-up form never shows the guest's email, password or stay code when
// it is printed, for example in a log line or an exception message (QA Manual 3.4, RIA-141).
class SignUpGuestRequestTest {

  private static final String EMAIL = "leonor.vieira.gdpr@example.test";
  private static final String PASSWORD = "Mare-Alta-Gdpr-2026";
  private static final String STAY_CODE = "H5N7R3KQ";

  @Requirement("RIA-51")
  @Test
  void shouldHideTheEmailPasswordAndStayCodeWhenPrinted() {
    var request = new SignUpGuestRequest(EMAIL, PASSWORD, STAY_CODE, "pt");

    assertThat(request.toString()).doesNotContain(EMAIL, PASSWORD, STAY_CODE).contains("locale=pt");
  }
}
