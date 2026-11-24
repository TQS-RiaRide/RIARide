package com.riaride.accounts.internal.persistence;

import com.riaride.accounts.internal.domain.StayCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
class StayCodeConverter implements AttributeConverter<StayCode, String> {

  @Override
  public String convertToDatabaseColumn(StayCode code) {
    return code == null ? null : code.value();
  }

  @Override
  public StayCode convertToEntityAttribute(String value) {
    return value == null ? null : new StayCode(value);
  }
}
