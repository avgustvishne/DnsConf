package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EOL = Pattern.compile("\\r?\\n");

    public static String removeWWW(String domain) {
        if (domain.startsWith("www.")) {
            return domain.substring("www.".length());
        }
        return domain;
    }

    public static boolean isComment(String line) {
        return line.startsWith("#");
    }

    public static @Nullable HostsLine parseHostsLine(String line) {
        return parseHostsLines(line).findFirst().orElse(null);
    }

    /**
     * Parses a hosts line into all its entries. Standard hosts files allow more than
     * one hostname to share the same IP address (for example: "127.0.0.1 a.test b.test").
     */
    public static Stream<HostsLine> parseHostsLines(String line) {
        String sanitizedLine = stripInlineComment(line);
        if (sanitizedLine.isBlank()) {
            return Stream.empty();
        }

        String[] columns = WHITESPACE.split(sanitizedLine.strip());
        if (columns.length == 1) {
            String value = columns[0];
            return Stream.of(isValidIP(value) ? HostsLine.ipOnly(value) : HostsLine.domainOnly(removeWWW(value)));
        }

        String ip = columns[0];
        if (isValidIP(ip)) {
            return java.util.Arrays.stream(columns)
                    .skip(1)
                    .map(DataParser::removeWWW)
                    .map(domain -> new HostsLine(ip, domain));
        }

        Log.fail("Failed to parse hosts line: " + line);
        return Stream.empty();
    }

    public static Stream<String> splitByEol(String data) {
        return EOL.splitAsStream(data);
    }

    private static String stripInlineComment(String line) {
        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            return line.substring(0, commentIndex).strip();
        }
        return line;
    }

    private static boolean isValidIP(String ip) {
        try {
            return !InetAddress.ofLiteral(ip).getHostAddress().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
