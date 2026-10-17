package com.novibe.common.util;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.base_structures.DnsProfile;
import com.novibe.common.exception.UserInputException;
import lombok.Cleanup;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public class DonorDnsUtils {

    public static void replaceIPs(List<BypassRoute> bypassRoutes, DnsProfile dnsProfile) {
        Resolver dnsResolver = getDnsResolver(dnsProfile);
        dnsResolver.setTimeout(Duration.ofSeconds(5));

        // Do not return until every lookup has completed. Otherwise the caller can
        // start writing provider rules while these tasks are still changing route IPs.
        @Cleanup ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        var futures = bypassRoutes.stream()
                .map(route -> executor.submit(() -> replaceIp(route, dnsResolver)))
                .toList();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while resolving donor DNS addresses", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to resolve donor DNS address", e.getCause());
            }
        }
    }

    private static void replaceIp(BypassRoute bypassRoute, Resolver dnsResolver) {
        SequencedSet<String> donorIps = fetchDonorIps(bypassRoute.website(), dnsResolver);
        String newIp = chooseIp(bypassRoute.ip(), donorIps);
        if (!bypassRoute.ip().equals(newIp)) {
            Log.common("Changed IP for %s: %s -> %s".formatted(bypassRoute.website(), bypassRoute.ip(), newIp));
            bypassRoute.ip(newIp);
        }
    }

    // Keep the configured IP when the donor still advertises it. If not, only
    // switch to an address of the same family; an A-only/AAAA-only response must
    // not silently change an IPv4 route into IPv6 or vice versa.
    static String chooseIp(String currentIp, SequencedSet<String> donorIps) {
        if (donorIps.isEmpty() || donorIps.contains(currentIp)) {
            return currentIp;
        }
        boolean currentIsIpv6 = currentIp.contains(":");
        return donorIps.stream()
                .filter(ip -> ip.contains(":") == currentIsIpv6)
                .findFirst()
                .orElse(currentIp);
    }

    private static Resolver getDnsResolver(DnsProfile dnsProfile) {
        String dns = dnsProfile.donorDns();
        try {
            if (dns.startsWith("http://") || dns.startsWith("https://")) {
                return new DohResolver(dns);
            } else if (Address.isDottedQuad(dns) || dns.contains(":")) {
                return new SimpleResolver(dns);
            } else {
                throw new UnknownHostException();
            }
        } catch (UnknownHostException e) {
            throw UserInputException.noStackTrace(
                    "Invalid DONOR_DNS value: %s. Value must be an IPv4/IPv6 address or a DoH URL".formatted(dns));
        }
    }

    private static SequencedSet<String> fetchDonorIps(String domain, Resolver resolver) {
        SequencedSet<String> addresses = new LinkedHashSet<>();
        addRecords(domain, Type.A, resolver, addresses);
        addRecords(domain, Type.AAAA, resolver, addresses);
        return addresses;
    }

    private static void addRecords(String domain, int recordType, Resolver resolver, SequencedSet<String> addresses) {
        try {
            Lookup lookup = new Lookup(domain, recordType);
            lookup.setResolver(resolver);
            Record[] records = lookup.run();
            if (isNull(records)) {
                return;
            }
            Arrays.stream(records)
                    .filter(record -> recordType == Type.A
                            ? record instanceof ARecord
                            : record instanceof AAAARecord)
                    .map(record -> recordType == Type.A
                            ? ((ARecord) record).getAddress().getHostAddress()
                            : ((AAAARecord) record).getAddress().getHostAddress())
                    .forEach(addresses::add);
        } catch (TextParseException e) {
            Log.fail("Invalid domain address: " + domain);
        }
    }
}
