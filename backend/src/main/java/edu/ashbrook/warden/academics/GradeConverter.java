package edu.ashbrook.warden.academics;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GradeConverter implements AttributeConverter<Grade, String> {

    @Override
    public String convertToDatabaseColumn(Grade grade) {
        return grade == null ? null : grade.symbol();
    }

    @Override
    public Grade convertToEntityAttribute(String symbol) {
        return symbol == null ? null : Grade.fromSymbol(symbol);
    }
}
