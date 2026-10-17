package com.novibe.common.util;

import com.novibe.common.base_structures.HostsLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataParserTest {

    @Test
    void parsesMultipleHostnamesForOneIp() {
        List<HostsLine> lines = DataParser.parseHostsLines("1.2.3.4 www.example.com api.example.com")
                .toList();

        assertEquals(List.of("example.com", "api.example.com"),
                lines.stream().map(HostsLine::domain).toList());
        assertEquals(List.of("1.2.3.4", "1.2.3.4"),
                lines.stream().map(HostsLine::ip).toList());
    }

    @Test
    void ignoresInlineCommentsWhenParsingAliases() {
        List<HostsLine> lines = DataParser.parseHostsLines("127.0.0.1 ads.example test.example # local block")
                .toList();

        assertEquals(List.of("ads.example", "test.example"),
                lines.stream().map(HostsLine::domain).toList());
    }

    @Test
    void preservesDomainOnlyBlocklistEntries() {
        List<HostsLine> lines = DataParser.parseHostsLines("example.com").toList();

        assertEquals(1, lines.size());
        assertEquals("example.com", lines.getFirst().domain());
        assertEquals(null, lines.getFirst().ip());
    }

    @Test
    void skipsBlankAndCommentOnlyLines() {
        assertEquals(List.of(), DataParser.parseHostsLines("   # comment").toList());
        assertEquals(List.of(), DataParser.parseHostsLines("  ").toList());
    }
}
