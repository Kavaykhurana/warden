package edu.ashbrook.warden.academics;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import edu.ashbrook.warden.common.FieldValidationException;

public enum Grade {
    A_PLUS("A+", 10), A("A", 9), B_PLUS("B+", 8), B("B", 7), C_PLUS("C+", 6), C("C", 5), D("D", 4), F("F", 0);

    private final String symbol;
    private final int points;

    Grade(String symbol, int points) {
        this.symbol = symbol;
        this.points = points;
    }

    @JsonValue
    public String symbol() {
        return symbol;
    }

    public int points() {
        return points;
    }

    @JsonCreator
    public static Grade fromSymbol(String symbol) {
        String wanted = symbol == null ? "" : symbol.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(g -> g.symbol.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException("newGrade", "Use one of A+, A, B+, B, C+, C, D, F."));
    }

    @Override
    public String toString() {
        return symbol;
    }
}
