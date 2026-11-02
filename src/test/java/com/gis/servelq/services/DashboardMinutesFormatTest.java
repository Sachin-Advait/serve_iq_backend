package com.gis.servelq.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMinutesFormatTest {

    @Test
    void showsOnlyTheUnitsNeeded() {
        assertThat(DashboardService.formatMinutes(0.4)).isEqualTo("0.4min");
        assertThat(DashboardService.formatMinutes(45.2)).isEqualTo("45min");
        assertThat(DashboardService.formatMinutes(447.4)).isEqualTo("7h 27min");
        assertThat(DashboardService.formatMinutes(120)).isEqualTo("2h 0min");
        assertThat(DashboardService.formatMinutes(1440 + 125)).isEqualTo("1d 2h 5min");
        assertThat(DashboardService.formatMinutes(1445)).isEqualTo("1d 0h 5min");
    }
}
