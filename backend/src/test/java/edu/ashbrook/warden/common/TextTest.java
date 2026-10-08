package edu.ashbrook.warden.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void truncateKeepsShortValuesAndCutsLongOnes() {
        assertThat(Text.truncate("abc", 5)).isEqualTo("abc");
        assertThat(Text.truncate("abcdef", 3)).isEqualTo("abc");
        assertThat(Text.truncate(null, 3)).isEmpty();
        assertThat(Text.truncate("a\u0000b", 5)).isEqualTo("ab");
    }

    @Test
    void truncateNeverLeavesHalfAnEmoji() {
        String ball = "🏀";
        assertThat(Text.truncate("ab" + ball, 3)).isEqualTo("ab");
        assertThat(Text.truncate("ab" + ball, 4)).isEqualTo("ab" + ball);
        assertThat(Text.truncate("a" + ball + "z", 3)).isEqualTo("a" + ball);
    }
}
