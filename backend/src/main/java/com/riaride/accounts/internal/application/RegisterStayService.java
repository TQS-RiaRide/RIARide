package com.riaride.accounts.internal.application;

import com.riaride.accounts.internal.domain.Stay;
import com.riaride.accounts.internal.domain.StayCode;
import com.riaride.accounts.internal.domain.StayCodeGenerator;
import com.riaride.accounts.internal.persistence.HotelRepository;
import com.riaride.accounts.internal.persistence.StayRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Registers a guest's stay and issues its stay code.
@Service
public class RegisterStayService {

  static final int MAX_CODE_ATTEMPTS = 5;

  private final HotelRepository hotels;
  private final StayRepository stays;
  private final StayCodeGenerator codes;
  private final Clock clock;

  public RegisterStayService(
      HotelRepository hotels, StayRepository stays, StayCodeGenerator codes, Clock clock) {
    this.hotels = hotels;
    this.stays = stays;
    this.codes = codes;
    this.clock = clock;
  }

  @Transactional
  public Stay register(RegisterStayCommand command, StayRegistrar registrar) {
    if (!registrar.canRegisterAt(command.hotelId())) {
      throw new HotelAccessDeniedException();
    }
    if (!hotels.existsById(command.hotelId())) {
      throw new HotelNotFoundException(command.hotelId());
    }
    var stay =
        Stay.register(
            command.hotelId(),
            command.guestName(),
            freeCode(),
            command.checkIn(),
            command.checkOut(),
            LocalDate.now(clock));
    return stays.save(stay);
  }

  // The unique constraint on stay_code is the final guard against a concurrent duplicate.
  private StayCode freeCode() {
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      var code = codes.next();
      if (!stays.existsByStayCode(code)) {
        return code;
      }
    }
    throw new StayCodeUnavailableException(MAX_CODE_ATTEMPTS);
  }
}
