package com.gis.servelq.services;

import com.gis.servelq.dto.TabularReportDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.repository.BranchRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * E-mails the operational reports as Excel attachments on a schedule
 * (06:00 daily by default), one file per report with a sheet per branch.
 * Recipients, schedule, period and the list of reports come from .env
 * (REPORT_MAIL_*); the SMTP server from MAIL_*. Nothing is sent while
 * MAIL_HOST is blank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportMailService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** The reports that can be attached, by the name used in REPORT_MAIL_REPORTS. */
    private interface ReportSource {
        TabularReportDTO build(String branchId, LocalDate from, LocalDate to);
    }

    private final OperationsReportService reports;
    private final BranchRepository branchRepository;
    private final ReportExcelWriter excelWriter;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.report-mail.to:}")
    private String to;

    @Value("${app.report-mail.from:}")
    private String from;

    @Value("${app.report-mail.days:1}")
    private int days;

    @Value("${app.report-mail.reports:}")
    private String reportNames;

    public record Result(boolean sent, String message, List<String> recipients, List<String> attachments) {
    }

    @Scheduled(cron = "${app.report-mail.cron:0 0 6 * * *}")
    public void sendScheduled() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate start = yesterday.minusDays(Math.max(days, 1) - 1L);
        try {
            Result result = send(start, yesterday);
            log.info("Scheduled report e-mail: {}", result.message());
        } catch (Exception e) {
            log.error("Scheduled report e-mail failed", e);
        }
    }

    /** Builds the configured reports for every enabled branch and e-mails them. */
    public Result send(LocalDate start, LocalDate end) throws Exception {
        List<String> recipients = splitList(to);
        if (isBlank(mailHost)) {
            return new Result(false, "MAIL_HOST is not set - no e-mail sent", recipients, List.of());
        }
        if (recipients.isEmpty()) {
            return new Result(false, "REPORT_MAIL_TO is empty - no e-mail sent", recipients, List.of());
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must not be after to");
        }

        Map<String, ReportSource> sources = sources();
        List<String> wanted = splitList(reportNames).stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        List<String> unknown = wanted.stream().filter(n -> !sources.containsKey(n)).toList();
        if (!unknown.isEmpty()) {
            log.warn("REPORT_MAIL_REPORTS has unknown report(s) {}; known: {}", unknown, sources.keySet());
        }
        List<String> chosen = wanted.stream().filter(sources::containsKey).distinct().toList();
        if (chosen.isEmpty()) {
            return new Result(false, "No known report in REPORT_MAIL_REPORTS - no e-mail sent", recipients, List.of());
        }

        List<Branch> branches = branchRepository.findAll().stream()
                .filter(b -> !Boolean.FALSE.equals(b.getEnabled()))
                .sorted(Comparator.comparing(b -> b.getName() == null ? "" : b.getName()))
                .toList();

        String period = start.equals(end) ? DATE.format(start) : DATE.format(start) + " to " + DATE.format(end);
        String fileSuffix = start.equals(end) ? DATE.format(start) : DATE.format(start) + "_" + DATE.format(end);

        Map<String, byte[]> files = new LinkedHashMap<>();
        List<String> titles = new ArrayList<>();
        for (String name : chosen) {
            List<TabularReportDTO> perBranch = new ArrayList<>();
            for (Branch branch : branches) {
                perBranch.add(sources.get(name).build(branch.getId(), start, end));
            }
            if (perBranch.isEmpty()) continue;
            files.put(name + "_" + fileSuffix + ".xlsx", excelWriter.write(perBranch));
            titles.add(perBranch.get(0).getTitle());
        }
        if (files.isEmpty()) {
            return new Result(false, "No branches - no e-mail sent", recipients, List.of());
        }

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            return new Result(false, "No mail sender configured - no e-mail sent", recipients, List.of());
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        String sendFrom = !isBlank(from) ? from : mailUsername;
        if (!isBlank(sendFrom)) helper.setFrom(sendFrom);
        helper.setTo(recipients.toArray(String[]::new));
        helper.setSubject("Visitor reports - " + period);
        helper.setText(body(period, titles, branches));
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            helper.addAttachment(file.getKey(), new ByteArrayResource(file.getValue()), XLSX);
        }
        sender.send(message);

        List<String> attached = new ArrayList<>(files.keySet());
        return new Result(true, "Sent " + attached.size() + " report(s) for " + period + " to "
                + String.join(", ", recipients), recipients, attached);
    }

    private Map<String, ReportSource> sources() {
        Map<String, ReportSource> m = new LinkedHashMap<>();
        m.put("agent-performance", reports::agentPerformance);
        m.put("waiting-sla", reports::waitingAndSla);
        m.put("no-show", reports::noShowAnalysis);
        m.put("service-demand", reports::serviceDemand);
        m.put("agent-utilization", reports::agentUtilization);
        return m;
    }

    private static String body(String period, List<String> titles, List<Branch> branches) {
        StringBuilder sb = new StringBuilder();
        sb.append("Visitor Management reports for ").append(period).append(".\n\n");
        sb.append("Attached (one Excel file per report, one sheet per branch):\n");
        for (String t : titles) sb.append("  - ").append(t).append('\n');
        sb.append("\nBranches: ").append(String.join(", ",
                branches.stream().map(Branch::getName).toList())).append("\n\n");
        sb.append("This e-mail was sent automatically.");
        return sb.toString();
    }

    private static List<String> splitList(String value) {
        if (value == null) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
