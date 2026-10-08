package edu.ashbrook.warden.academics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import edu.ashbrook.warden.common.FieldValidationException;

class GradeTest {

    @Test
    void pointsFollowTheTenPointScale() {
        assertThat(Grade.values()).extracting(Grade::points).containsExactly(10, 9, 8, 7, 6, 5, 4, 0);
        assertThat(Grade.values()).extracting(Grade::symbol).containsExactly("A+", "A", "B+", "B", "C+", "C", "D", "F");
    }

    @Test
    void parsesSymbolsLeniently() {
        assertThat(Grade.fromSymbol("B+")).isEqualTo(Grade.B_PLUS);
        assertThat(Grade.fromSymbol(" a+ ")).isEqualTo(Grade.A_PLUS);
        assertThat(Grade.fromSymbol("f")).isEqualTo(Grade.F);
    }

    @Test
    void rejectsUnknownSymbols() {
        assertThatThrownBy(() -> Grade.fromSymbol("E")).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> Grade.fromSymbol("A++")).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> Grade.fromSymbol(null)).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void converterStoresTheSymbol() {
        GradeConverter converter = new GradeConverter();
        assertThat(converter.convertToDatabaseColumn(Grade.C_PLUS)).isEqualTo("C+");
        assertThat(converter.convertToEntityAttribute("C+")).isEqualTo(Grade.C_PLUS);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}
