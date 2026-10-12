package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.TicketIssuingClosedException;
import com.gis.servelq.dto.TicketIssuingRequest;
import com.gis.servelq.models.Branch;
import com.gis.servelq.repository.BranchRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ticket issuing control (queue procedure section 10): the supervisor can stop
 * new tickets, and tickets are only issued inside the branch's hours.
 */
@ExtendWith(MockitoExtension.class)
class TicketIssuingTest {

    @Mock private AuditLogService auditLogService;
    @Mock private HttpServletRequest request;
    @Mock private BranchRepository branchRepository;

    private BranchService branchService;
    private Branch branch;

    @BeforeEach
    void setUp() {
        branchService = new BranchService(auditLogService, request, branchRepository);
        branch = new Branch();
        branch.setId("branch-1");
        branch.setCode("B1");
        branch.setName("Main hall");
        lenient().when(branchRepository.findById("branch-1")).thenReturn(Optional.of(branch));
        lenient().when(branchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void issuesByDefault() {
        assertThatCode(() -> branchService.assertTicketIssuingOpen("branch-1", "en")).doesNotThrowAnyException();
    }

    @Test
    void stoppedIssuingShowsTheDefaultMessageInTheKioskLanguage() {
        branch.setTicketIssuingEnabled(false);

        assertThatThrownBy(() -> branchService.assertTicketIssuingOpen("branch-1", "en"))
                .isInstanceOf(TicketIssuingClosedException.class)
                .hasMessageContaining("Ticket issuing has stopped");
        assertThatThrownBy(() -> branchService.assertTicketIssuingOpen("branch-1", "ar"))
                .isInstanceOf(TicketIssuingClosedException.class)
                .hasMessageContaining("توقف إصدار التذاكر");
    }

    @Test
    void stoppedIssuingUsesTheSupervisorsMessage() {
        branch.setTicketIssuingEnabled(false);
        branch.setTicketStopMessageEn("The hall is full for today.");

        assertThatThrownBy(() -> branchService.assertTicketIssuingOpen("branch-1", "en"))
                .hasMessage("The hall is full for today.");
    }

    @Test
    void refusesOutsideTheIssuingHours() {
        LocalTime now = LocalTime.now();
        // A one-minute window that has already closed (or, just after midnight, not opened yet)
        LocalTime start = now.isAfter(LocalTime.of(0, 10)) ? now.minusMinutes(5) : now.plusMinutes(2);
        branch.setTicketIssuingStart(start);
        branch.setTicketIssuingEnd(start.plusMinutes(1));

        assertThatThrownBy(() -> branchService.assertTicketIssuingOpen("branch-1", "en"))
                .isInstanceOf(TicketIssuingClosedException.class)
                .hasMessageStartingWith("Tickets are issued from");
    }

    @Test
    void rejectsHoursThatEndBeforeTheyStart() {
        TicketIssuingRequest body = new TicketIssuingRequest();
        body.setEnabled(true);
        body.setStart(LocalTime.of(13, 0));
        body.setEnd(LocalTime.of(8, 0));

        assertThatThrownBy(() -> branchService.updateTicketIssuing("branch-1", body, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void savesTheStopAndHours() {
        TicketIssuingRequest body = new TicketIssuingRequest();
        body.setEnabled(false);
        body.setStart(LocalTime.of(7, 30));
        body.setEnd(LocalTime.of(13, 30));
        body.setMessageEn("  ");

        var saved = branchService.updateTicketIssuing("branch-1", body, null);

        org.assertj.core.api.Assertions.assertThat(saved.getTicketIssuingEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(saved.getTicketIssuingStart()).isEqualTo(LocalTime.of(7, 30));
        org.assertj.core.api.Assertions.assertThat(saved.getTicketStopMessageEn()).isNull();
    }
}
