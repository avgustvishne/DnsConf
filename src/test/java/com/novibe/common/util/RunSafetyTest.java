package com.novibe.common.util;

import com.novibe.common.exception.UserInputException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSafetyTest {

    @Test
    void rejectsEmptyConfigurationByDefault() {
        UserInputException error = assertThrows(UserInputException.class,
                () -> RunSafety.requireNonEmpty("BLOCK", List.of(), false));
        assertTrue(error.getMessage().contains("ALLOW_EMPTY_CONFIG=true"));
    }

    @Test
    void permitsExplicitlyAllowedEmptyConfiguration() {
        assertDoesNotThrow(() -> RunSafety.requireNonEmpty("BLOCK", List.of(), true));
    }

    @Test
    void acceptsNonEmptyConfiguration() {
        assertDoesNotThrow(() -> RunSafety.requireNonEmpty("BLOCK", List.of("example.com"), false));
    }

    @Test
    void parsesBooleanEnvironmentFlagsExplicitly() {
        assertTrue(RunSafety.readFlag("DRY_RUN", "true", false));
        assertTrue(RunSafety.readFlag("DRY_RUN", " YES ", false));
        assertFalse(RunSafety.readFlag("DRY_RUN", "false", true));
        assertFalse(RunSafety.readFlag("DRY_RUN", null, false));
    }

    @Test
    void rejectsMisspelledBooleanFlags() {
        assertThrows(UserInputException.class, () -> RunSafety.readFlag("DRY_RUN", "tru", false));
    }
}
