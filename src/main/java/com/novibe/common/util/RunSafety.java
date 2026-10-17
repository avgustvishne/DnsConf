package com.novibe.common.util;

import com.novibe.common.exception.UserInputException;

import java.util.Collection;
import java.util.Locale;

/**
 * Safety switches for operations that may change remote DNS configuration.
 *
 * Empty configurations are rejected by default because both providers can
 * interpret an empty input as a request to remove previously managed rules.
 */
public final class RunSafety {

    private RunSafety() {
    }

    public static boolean isDryRun() {
        return readFlag("DRY_RUN", System.getenv("DRY_RUN"), false);
    }

    public static boolean allowEmptyConfig() {
        return readFlag("ALLOW_EMPTY_CONFIG", System.getenv("ALLOW_EMPTY_CONFIG"), false);
    }

    static boolean readFlag(String name, String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw UserInputException.noStackTrace(
                    "Invalid boolean value for %s: expected true/false, yes/no, on/off or 1/0".formatted(name));
        };
    }

    public static void requireNonEmpty(String description, Collection<?> values, boolean allowEmpty) {
        if (values.isEmpty() && !allowEmpty) {
            throw UserInputException.noStackTrace(
                    ("Refusing to apply an empty %s configuration. Check source URLs and parsed entries. "
                            + "If clearing managed DNS rules is intentional, set ALLOW_EMPTY_CONFIG=true.")
                            .formatted(description));
        }
    }

    public static void reportDryRun(String provider, int blockCount, int redirectCount) {
        Log.global("DRY RUN — no DNS provider changes will be sent");
        Log.common("Provider: " + provider);
        Log.common("Validated block entries: " + blockCount);
        Log.common("Validated redirect entries: " + redirectCount);
        Log.common("No managed rules or lists were created, updated, or deleted.");
    }
}
