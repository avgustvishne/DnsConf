package com.novibe.common.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DonorDnsUtilsTest {

    private static SequencedSet<String> donorIps(String... ips) {
        return new LinkedHashSet<>(List.of(ips));
    }

    @Test
    void keepsCurrentIpWhenDonorStillReturnsIt() {
        assertEquals("1.1.1.2", DonorDnsUtils.chooseIp("1.1.1.2", donorIps("1.1.1.1", "1.1.1.2", "1.1.1.3")));
    }

    @Test
    void takesFirstDonorIpOfSameFamilyWhenCurrentIpIsOutdated() {
        assertEquals("1.1.1.1", DonorDnsUtils.chooseIp("9.9.9.9", donorIps("1.1.1.1", "1.1.1.2")));
    }

    @Test
    void doesNotReplaceIpv4WithIpv6() {
        assertEquals("9.9.9.9", DonorDnsUtils.chooseIp("9.9.9.9", donorIps("2001:db8::1")));
    }

    @Test
    void doesNotReplaceIpv6WithIpv4() {
        assertEquals("2001:db8::9", DonorDnsUtils.chooseIp("2001:db8::9", donorIps("1.1.1.1")));
    }

    @Test
    void choosesIpv6WhenCurrentIpv6IsOutdated() {
        assertEquals("2001:db8::1", DonorDnsUtils.chooseIp("2001:db8::9", donorIps("1.1.1.1", "2001:db8::1")));
    }

    @Test
    void keepsCurrentIpWhenDonorReturnsNothing() {
        assertEquals("9.9.9.9", DonorDnsUtils.chooseIp("9.9.9.9", donorIps()));
    }
}
