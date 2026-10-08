package com.gis.servelq.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Signs everyone out once a day (07:00 server time by default) so each day starts
 * with fresh logins: held counters are released and every login token is invalidated.
 * Set app.daily-logout.cron to "-" to turn it off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyLogoutScheduler {

    private final CounterService counterService;

    @Scheduled(cron = "${app.daily-logout.cron:0 0 7 * * *}", zone = "${app.daily-logout.zone:}")
    public void signOutEveryone() {
        try {
            int released = counterService.signOutEveryone();
            log.info("Daily sign-out: all login tokens invalidated, {} counter(s) released", released);
        } catch (Exception e) {
            log.error("Daily sign-out failed", e);
        }
    }
}
