package com.novibe.dns.cloudflare;

import com.novibe.common.DnsTaskRunner;
import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.util.DonorDnsUtils;
import com.novibe.common.util.EnvParser;
import com.novibe.common.util.Log;
import com.novibe.common.util.RunSafety;
import com.novibe.dns.cloudflare.http.dto.response.list.GatewayListDto;
import com.novibe.dns.cloudflare.http.dto.response.rule.GatewayRuleDto;
import com.novibe.dns.cloudflare.service.ListService;
import com.novibe.dns.cloudflare.service.RulePrecedenceCounter;
import com.novibe.dns.cloudflare.service.RuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.BLOCK;
import static com.novibe.common.config.EnvironmentVariables.REDIRECT;
import static java.util.Objects.nonNull;


@Service
@RequiredArgsConstructor
public class CloudflareTaskRunner extends DnsTaskRunner {

    private final ListService listService;
    private final RuleService ruleService;


    @Override
    protected void greetingMessage() {

        Log.global("Setting up Profile " + dnsProfile.number() + " (CLOUDFLARE)");
        Log.common("""
                Script behaviour: previously generated data is always about to be removed.
                - if you want to clear Cloudflare BLOCK/REDIRECT settings, launch this script without providing sources to related environment variables.
                - each line is mapped to an IP–domain pair; lines that cannot be parsed are skipped.
                """);
    }

    @Override
    public void process() {


        List<String> blocks = blockListsLoader.fetchWebsites(EnvParser.parse(BLOCK));
        List<BypassRoute> overrides = overrideListsLoader.fetchWebsites(EnvParser.parse(REDIRECT));
        listService.omitExcludedOverrides(overrides);

        if (nonNull(dnsProfile.donorDns())) {
            Log.step("Replace IP of domains via IPs from " + dnsProfile.donorDns());
            DonorDnsUtils.replaceIPs(overrides, dnsProfile);
        }

        boolean allowEmpty = RunSafety.allowEmptyConfig();
        RunSafety.requireNonEmpty("BLOCK", blocks, allowEmpty);
        RunSafety.requireNonEmpty("REDIRECT", overrides, allowEmpty);

        if (RunSafety.isDryRun()) {
            RunSafety.reportDryRun("Cloudflare", blocks.size(), overrides.size());
            return;
        }

        Log.step("Remove old rules.");
        List<GatewayRuleDto> gatewayRuleDtos = ruleService.obtainExistingRules();
        List<GatewayRuleDto> remainingRules = ruleService.removeOldRules(gatewayRuleDtos);
        RulePrecedenceCounter precedenceCounter = RulePrecedenceCounter.providePrecedenceCounter(remainingRules);

        Log.step("Remove old lists.");
        listService.removeOldLists();

        Log.step("Creating new block lists");
        if (!blocks.isEmpty()) {
            List<GatewayListDto> gatewayListDtos = listService.createNewBlockLists(blocks);

            Log.step("Creating new blocking rule");
            ruleService.createNewBlockingRule(gatewayListDtos, precedenceCounter);
        } else {
            Log.fail("Websites to block were not provided");
        }

        Log.step("Creating new override lists");
        if (!overrides.isEmpty()) {
            Map<String, List<GatewayListDto>> newOverrideLists = listService.createNewOverrideLists(overrides);

            Log.step("Creating new override rules");
            ruleService.createNewOverrideRules(newOverrideLists, precedenceCounter);
        } else {
            Log.fail("Websites to override were not provided");
        }
    }

    @Override
    protected void finishMessage() {
        if (RunSafety.isDryRun()) {
            Log.global("Profile " + dnsProfile.number() + " (Cloudflare) dry run completed; no changes applied");
        } else {
            Log.global("Profile " + dnsProfile.number() + " (Cloudflare) set up successfully");
        }
    }
}
