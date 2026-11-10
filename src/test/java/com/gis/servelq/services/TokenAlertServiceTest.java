package com.gis.servelq.services;

import com.gis.servelq.models.*;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.TokenAlertSettingsRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/** One mail per crossing of the waiting limit, to the chosen roles of that branch. */
@ExtendWith(MockitoExtension.class)
class TokenAlertServiceTest {

    @Mock private TokenAlertSettingsRepository settingsRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserRepository userRepository;
    @Mock private ObjectProvider<JavaMailSender> mailProvider;
    @Mock private JavaMailSender mailSender;

    private TokenAlertService service;
    private TokenAlertSettings settings;

    @BeforeEach
    void setUp() {
        service = new TokenAlertService(settingsRepository, tokenRepository, branchRepository,
                userRepository, mailProvider);
        ReflectionTestUtils.setField(service, "mailHost", "smtp.test");
        settings = new TokenAlertSettings();
        settings.setWaitingThreshold(20);
        settings.setRoles(new ArrayList<>(List.of("ADMIN", "MANAGER")));
        lenient().when(settingsRepository.findById(1)).thenReturn(Optional.of(settings));
        Branch b = new Branch();
        b.setId("b1");
        b.setName("Muscat");
        lenient().when(branchRepository.findById("b1")).thenReturn(Optional.of(b));
        lenient().when(mailProvider.getIfAvailable()).thenReturn(mailSender);
        lenient().when(userRepository.findByRoleIn(anyCollection())).thenReturn(List.of(
                user("a@x.com", UserRole.ADMIN, null, true),
                user("m@x.com", UserRole.MANAGER, "b1", true),
                user("other@x.com", UserRole.MANAGER, "b2", true),
                user("off@x.com", UserRole.ADMIN, null, false),
                user("noemail", UserRole.ADMIN, null, true)));
    }

    private static User user(String email, UserRole role, String branch, boolean active) {
        User u = new User();
        u.setEmail(email);
        u.setRole(role);
        u.setBranchId(branch);
        u.setActive(active);
        return u;
    }

    private void waiting(long n) {
        when(tokenRepository.countByStatusForBranch("b1")).thenReturn(List.<Object[]>of(
                new Object[]{TokenStatus.WAITING, n}, new Object[]{TokenStatus.DONE, 5L}));
    }

    @Test
    void mailsOncePerCrossing() {
        waiting(19);
        service.check("b1");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        waiting(20);
        service.check("b1");
        waiting(25);
        service.check("b1");
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        waiting(10); // drops below: re-armed
        service.check("b1");
        waiting(20);
        service.check("b1");
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsToActiveUsersOfTheBranchOrNoBranch() {
        waiting(21);
        service.check("b1");
        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(msg.capture());
        assertThat(msg.getValue().getTo()).containsExactly("a@x.com", "m@x.com");
        assertThat(msg.getValue().getSubject()).contains("Muscat").contains("21 waiting");
    }

    @Test
    void nothingWhenDisabledOrNoMailHost() {
        settings.setEnabled(false);
        service.check("b1");
        settings.setEnabled(true);
        ReflectionTestUtils.setField(service, "mailHost", "");
        waiting(30);
        service.check("b1");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
