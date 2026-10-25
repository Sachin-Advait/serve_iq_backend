package com.gis.servelq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * A report as headline figures plus tables. The admin panel shows and exports
 * every report of this shape the same way, so a new report needs no new screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TabularReportDTO {
    private String reportType;
    private String title;
    private String branchName;
    private LocalDate from;
    private LocalDate to;
    private List<Stat> summary;
    private List<Section> sections;
    // Notes on how figures are worked out, shown under the report
    private List<String> notes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stat {
        private String label;
        private String value;
        // GOOD, BAD or null, for colouring
        private String tone;

        public Stat(String label, String value) {
            this(label, value, null);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String title;
        private List<String> columns;
        private List<List<Object>> rows;
    }
}
