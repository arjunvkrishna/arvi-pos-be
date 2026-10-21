package com.neram.pos.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Time;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Converter(autoApply = true)
public class LocalTimeAttributeConverter implements AttributeConverter<LocalTime, Time> {

    @Override
    public Time convertToDatabaseColumn(LocalTime attribute) {
        if (attribute == null) {
            return null;
        }
        return Time.valueOf(attribute.truncatedTo(ChronoUnit.SECONDS));
    }

    @Override
    public LocalTime convertToEntityAttribute(Time dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData.toLocalTime();
    }
}
