package com.gis.servelq.services;

import com.gis.servelq.Exceptions.TicketIssuingClosedException;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Counter;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Stops ticket issuing on its own when the people already waiting for a
 * service can't be served before the office closes (procedure 10.b).
 * <p>
 * Estimate: (people waiting + the new visitor) x average service time
 * / counters serving the service. If now + estimate is after closing time,
 * the ticket is refused.
 */
@Service
@RequiredArgsConstructor
public class TicketCapacityService {

    // Used until a service has any completed tickets to average.
    static final int DEFAULT_SERVICE_MINUTES = 10;
    private static final int HISTORY_DAYS = 30;
    private static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("HH:mm");

    private final BranchRepository branchRepository;
    private final TokenRepository tokenRepository;
    private final CounterRepository counterRepository;

    public void assertQueueFitsBeforeClosing(String branchId, String serviceId, String language) {
        if (branchId == null || serviceId == null) return;
        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null || branch.getOfficeCloseTime() == null) return;

        LocalTime now = LocalTime.now();
        LocalTime close = branch.getOfficeCloseTime();
        LocalTime finish = estimateFinish(branchId, serviceId, now);

        if (finish == null || finish.isAfter(close)) {
            boolean arabic = "ar".equalsIgnoreCase(language) || "arabic".equalsIgnoreCase(language);
            String closeText = close.format(HOURS);
            throw new TicketIssuingClosedException(arabic
                    ? "لا يمكن خدمة زوار جدد لهذه الخدمة قبل موعد الإغلاق (" + closeText
                    + "). يرجى استخدام الخدمات الإلكترونية أو مركز الاتصال، أو زيارتنا يوم عمل آخر."
                    : "We can't serve more visitors for this service before closing time (" + closeText
                    + "). Please use our online services or the Contact Centre, or visit us on another working day.");
        }
    }

    /**
     * When the queue for the service would clear if one more visitor joined
     * now, or null when it runs past midnight.
     */
    LocalTime estimateFinish(String branchId, String serviceId, LocalTime now) {
        long waiting = tokenRepository.countByServiceIdAndStatus(serviceId, TokenStatus.WAITING)
                + tokenRepository.countByServiceIdAndStatus(serviceId, TokenStatus.HOLD);

        double minutesEach = averageServiceMinutes(serviceId);
        int counters = countersFor(branchId, serviceId);

        long minutes = (long) Math.ceil((waiting + 1) * minutesEach / counters);
        LocalTime finish = now.plusMinutes(minutes);
        // plusMinutes wraps around midnight
        if (minutes >= 24 * 60 || finish.isBefore(now)) return null;
        return finish;
    }

    private double averageServiceMinutes(String serviceId) {
        LocalDate today = LocalDate.now();
        Double seconds = tokenRepository.getAvgServiceSecondsForService(serviceId, today);
        if (seconds == null || seconds <= 0) {
            seconds = tokenRepository.getAvgServiceSecondsForService(serviceId, today.minusDays(HISTORY_DAYS));
        }
        if (seconds == null || seconds <= 0) return DEFAULT_SERVICE_MINUTES;
        return Math.max(1.0, seconds / 60.0);
    }

    // Counters with an agent logged in that serve the service. Before anyone
    // has logged in, count every enabled counter for it.
    private int countersFor(String branchId, String serviceId) {
        List<Counter> forService = counterRepository.findByBranchId(branchId).stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .filter(c -> CounterServiceLinker.serviceIdsOf(c).contains(serviceId))
                .toList();
        long staffed = forService.stream()
                .filter(c -> c.getUserId() != null && !Boolean.TRUE.equals(c.getPaused()))
                .count();
        return (int) Math.max(1, staffed > 0 ? staffed : forService.size());
    }
}
