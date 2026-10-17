package com.novibe.dns.next_dns;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.util.DonorDnsUtils;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.common.util.RunSafety;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.service.NextDnsDenyService;
import com.novibe.dns.next_dns.service.NextDnsRewriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsTaskRunner extends DnsTaskRunner {

    private final NextDnsRewriteService nextDnsRewriteService;
    private final NextDnsDenyService nextDnsDenyService;

    @Override
    public void greetingMessage() {
        Log.global("Setting up Profile " + dnsProfile.number() + " (NextDNS)");
        Log.common("""
                Script behaviour: old BLOCK/REDIRECT settings are about to be updated via provided BLOCK/REDIRECT sources.
                - if no sources provided, then all NextDNS settings will be removed.
                - each line is mapped to an IP–domain pair; lines that cannot be parsed are skipped.
                - if provided only one type of sources, related settings will be updated; another type remain untouched.
                - if EXCLUDE_REDIRECT domains provided, they will affect both existing and new redirect rules.
                NextDNS api rate limiter reset config: 60 seconds after the last request""");
    }

    @Override
    protected void process() {
        List<String> blockSources = EnvParser.parse(BLOCK);
        List<String> rewriteSources = EnvParser.parse(REDIRECT);

        List<String> blocks = blockSources.isEmpty()
                ? List.of()
                : blockListsLoader.fetchWebsites(blockSources);
        List<BypassRoute> overrides = rewriteSources.isEmpty()
                ? List.of()
                : overrideListsLoader.fetchWebsites(rewriteSources);

        boolean allowEmpty = RunSafety.allowEmptyConfig();
        if (blockSources.isEmpty() && rewriteSources.isEmpty()) {
            RunSafety.requireNonEmpty("BLOCK and REDIRECT sources", List.of(), allowEmpty);
        }
        if (!blockSources.isEmpty()) {
            RunSafety.requireNonEmpty("BLOCK list", blocks, allowEmpty);
        }
        if (!rewriteSources.isEmpty()) {
            RunSafety.requireNonEmpty("REDIRECT list", overrides, allowEmpty);
        }

        if (nonNull(dnsProfile.donorDns()) && !overrides.isEmpty()) {
            Log.step("Replace IP of domains via IPs from " + dnsProfile.donorDns());
            DonorDnsUtils.replaceIPs(overrides, dnsProfile);
        }

        if (RunSafety.isDryRun()) {
            RunSafety.reportDryRun("NextDNS", blocks.size(), overrides.size());
            return;
        }

        if (!blockSources.isEmpty()) {
            Log.step("Prepare denylist");
            List<String> filteredBlocklist = nextDnsDenyService.omitExistingDenys(blocks);
            Log.common("Prepared %s domains to block".formatted(filteredBlocklist.size()));
            Log.step("Save denylist");
            nextDnsDenyService.saveDenyList(filteredBlocklist);
        } else {
            Log.fail("No block sources provided; existing deny rules are left unchanged");
        }

        if (!rewriteSources.isEmpty()) {
            Log.step("Prepare rewrites");
            Map<String, CreateRewriteDto> requests = nextDnsRewriteService.buildNewRewrites(overrides);
            List<CreateRewriteDto> createRewriteDtos = nextDnsRewriteService.cleanupOutdatedAndExcluded(requests);

            Log.step("Save rewrites");
            nextDnsRewriteService.saveRewrites(createRewriteDtos);
        } else {
            Log.fail("No redirect sources provided; existing rewrite rules are left unchanged");
        }

        if (blockSources.isEmpty() && rewriteSources.isEmpty()) {
            Log.step("Remove settings (explicitly enabled by ALLOW_EMPTY_CONFIG)");
            nextDnsDenyService.removeAll();
            nextDnsRewriteService.removeAll();
        }
    }

    @Override
    protected void finishMessage() {
        if (RunSafety.isDryRun()) {
            Log.global("Profile " + dnsProfile.number() + " (NextDNS) dry run completed; no changes applied");
        } else {
            Log.global("Profile " + dnsProfile.number() + " (NextDNS) set up successfully");
        }
    }
}
