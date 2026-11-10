package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.TokenAlertSettingsDTO;
import com.gis.servelq.events.TokenEvent;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.TokenAlertSettings;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.models.User;
import com.gis.servelq.models.UserRole;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.TokenAlertSettingsRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E-mails the chosen roles when a branch's waiting tokens (today, status
 * WAITING) reach the threshold set on the admin panel's System page.
 * One mail per crossing: once a branch has been alerted it is not mailed
 * again until its waiting count drops below the threshold. Checked after
 * every token event of the branch. Nothing is sent while MAIL_HOST is blank.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenAlertService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final TokenAlertSettingsRepository settingsRepository;
    private final TokenRepository tokenRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<JavaMailSender> mailSender;

    /** Branches already mailed for the current crossing. */
    private final Set<String> alerted = ConcurrentHashMap.newKeySet();

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.report-mail.from:}")
    private String from;

    public record Result(boolean sent, String message, List<String> recipients) {
    }

    // ---- settings ----

    public TokenAlertSettings settings() {
        return settingsRepository.findById(TokenAlertSettings.SINGLETON_ID).orElseGet(TokenAlertSettings::new);
    }

    public TokenAlertSettingsDTO settingsDto() {
        TokenAlertSettings s = settings();
        TokenAlertSettingsDTO dto = new TokenAlertSettingsDTO();
        dto.setEnabled(!Boolean.FALSE.equals(s.getEnabled()));
        dto.setWaitingThreshold(s.getWaitingThreshold());
        dto.setRoles(s.getRoles() == null ? List.of() : s.getRoles());
        dto.setMailConfigured(!isBlank(mailHost));
        return dto;
    }

    public TokenAlertSettingsDTO updateSettings(TokenAlertSettingsDTO request) {
        TokenAlertSettings s = settings();
        if (request.getEnabled() != null) s.setEnabled(request.getEnabled());
        if (request.getWaitingThreshold() != null) {
            if (request.getWaitingThreshold() < 1) {
                throw new BusinessException("Waiting token limit must be at least 1");
            }
            s.setWaitingThreshold(request.getWaitingThreshold());
        }
        if (request.getRoles() != null) {
            s.setRoles(new ArrayList<>(parseRoles(request.getRoles()).stream().map(Enum::name).toList()));
        }
        settingsRepository.save(s);
        // New limit or roles: judge every branch afresh at its next token event
        alerted.clear();
        return settingsDto();
    }

    // ---- checking ----

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTokenEvent(TokenEvent event) {
        if (event.getBranchId() == null) return;
        try {
            check(event.getBranchId());
        } catch (Exception e) {
            log.error("Waiting token alert check failed for branch {}", event.getBranchId(), e);
        }
    }

    /** Mails the roles once when the branch's waiting count reaches the limit. */
    public void check(String branchId) {
        TokenAlertSettings s = settings();
        int threshold = s.getWaitingThreshold() == null ? 0 : s.getWaitingThreshold();
        if (Boolean.FALSE.equals(s.getEnabled()) || threshold < 1) return;

        long waiting = waitingCount(branchId);
        if (waiting < threshold) {
            alerted.remove(branchId);
            return;
        }
        if (isBlank(mailHost)) return;
        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null) return;
        List<String> recipients = recipients(s.getRoles(), branchId);
        if (recipients.isEmpty()) return;
        if (!alerted.add(branchId)) return; // already mailed for this crossing

        try {
            send(recipients, branch.getName(), waiting, threshold, false);
            log.info("Waiting token alert for {} ({} waiting, limit {}) sent to {}",
                    branch.getName(), waiting, threshold, recipients);
        } catch (Exception e) {
            log.error("Waiting token alert mail for {} failed", branch.getName(), e);
        }
    }

    /** Sends the alert for the branch now, whatever its count, to check the setup. */
    public Result sendTest(String branchId) {
        TokenAlertSettings s = settings();
        if (isBlank(mailHost)) {
            return new Result(false, "MAIL_HOST is not set in the backend .env - no e-mail sent", List.of());
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BusinessException("Branch not found"));
        List<String> recipients = recipients(s.getRoles(), branchId);
        if (recipients.isEmpty()) {
            return new Result(false, "No active user with the chosen roles has an e-mail address", List.of());
        }
        int threshold = s.getWaitingThreshold() == null ? TokenAlertSettings.DEFAULT_THRESHOLD : s.getWaitingThreshold();
        try {
            send(recipients, branch.getName(), waitingCount(branchId), threshold, true);
        } catch (Exception e) {
            log.error("Test waiting token alert failed", e);
            return new Result(false, "Mail server error: " + e.getMessage(), recipients);
        }
        return new Result(true, "Test e-mail sent to " + String.join(", ", recipients), recipients);
    }

    long waitingCount(String branchId) {
        for (Object[] row : tokenRepository.countByStatusForBranch(branchId)) {
            if (row[0] == TokenStatus.WAITING) return (Long) row[1];
        }
        return 0;
    }

    /**
     * Active users with one of the roles and an e-mail address, who belong to
     * this branch or to no branch (e.g. head-office admins).
     */
    List<String> recipients(List<String> roleNames, String branchId) {
        List<UserRole> roles = parseRoles(roleNames == null ? List.of() : roleNames);
        if (roles.isEmpty()) return List.of();
        return userRepository.findByRoleIn(roles).stream()
                .filter(u -> !Boolean.FALSE.equals(u.getActive()))
                .filter(u -> u.getBranchId() == null || u.getBranchId().isBlank() || u.getBranchId().equals(branchId))
                .map(User::getEmail)
                .filter(e -> e != null && e.contains("@"))
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void send(List<String> recipients, String branchName, long waiting, int threshold, boolean test) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) throw new IllegalStateException("No mail sender configured");
        SimpleMailMessage message = new SimpleMailMessage();
        String sendFrom = !isBlank(from) ? from : mailUsername;
        if (!isBlank(sendFrom)) message.setFrom(sendFrom);
        message.setTo(recipients.toArray(String[]::new));
        message.setSubject((test ? "[Test] " : "") + "Waiting tokens alert - " + branchName
                + ": " + waiting + " waiting");
        message.setText(body(branchName, waiting, threshold, test));
        sender.send(message);
    }

    static String body(String branchName, long waiting, int threshold, boolean test) {
        StringBuilder sb = new StringBuilder();
        if (test) {
            sb.append("This is a test of the waiting tokens alert.\n\n");
        }
        sb.append("Branch: ").append(branchName).append('\n');
        sb.append("Tokens waiting now: ").append(waiting).append('\n');
        sb.append("Alert limit: ").append(threshold).append('\n');
        sb.append("Time: ").append(TIME.format(LocalDateTime.now())).append("\n\n");
        if (!test) {
            sb.append("The waiting queue has reached the limit set in the admin panel (System page). ")
                    .append("No further alert is sent for this branch until the queue drops below the limit.\n\n");
        }
        sb.append("This e-mail was sent automatically.");
        return sb.toString();
    }

    private static List<UserRole> parseRoles(Collection<String> names) {
        Set<UserRole> out = new LinkedHashSet<>();
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            try {
                out.add(UserRole.valueOf(n.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Unknown role: " + n);
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
