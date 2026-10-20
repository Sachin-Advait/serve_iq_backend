package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.dto.TransferReasonRequest;
import com.gis.servelq.models.TransferReason;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.TransferReasonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferReasonServiceTest {

    @Mock private TransferReasonRepository transferReasonRepository;
    @Mock private TokenRepository tokenRepository;
    @InjectMocks private TransferReasonService service;

    private static TransferReason reason(String code, boolean active) {
        TransferReason r = new TransferReason();
        r.setId("id-" + code);
        r.setCode(code);
        r.setTitleEn("Title " + code);
        r.setTitleAr("عنوان");
        r.setActive(active);
        r.setSortOrder(1);
        return r;
    }

    @Test
    void createMakesCodeFromEnglishTitleAndAvoidsClashes() {
        when(transferReasonRepository.existsByCode("NEEDS_A_MANAGER")).thenReturn(true);
        when(transferReasonRepository.existsByCode("NEEDS_A_MANAGER_2")).thenReturn(false);
        when(transferReasonRepository.findAll()).thenReturn(List.of(reason("OTHER", true)));
        when(transferReasonRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferReasonRequest req = new TransferReasonRequest();
        req.setTitleEn("  Needs a manager! ");
        req.setTitleAr("يحتاج إلى مدير");
        TransferReason saved = service.create(req);

        assertEquals("NEEDS_A_MANAGER_2", saved.getCode());
        assertEquals("Needs a manager!", saved.getTitleEn());
        assertTrue(saved.getActive());
        assertEquals(2, saved.getSortOrder());
    }

    @Test
    void createNeedsBothTitles() {
        TransferReasonRequest req = new TransferReasonRequest();
        req.setTitleEn("Something");
        assertThrows(BusinessException.class, () -> service.create(req));
    }

    @Test
    void agentCannotPickAnInactiveOrUnknownReason() {
        when(transferReasonRepository.findByCode("OLD")).thenReturn(Optional.of(reason("OLD", false)));
        when(transferReasonRepository.findByCode("NOPE")).thenReturn(Optional.empty());
        when(transferReasonRepository.findByCode("OTHER")).thenReturn(Optional.of(reason("OTHER", true)));

        assertThrows(BusinessException.class, () -> service.requireActive("old"));
        assertThrows(BusinessException.class, () -> service.requireActive("NOPE"));
        assertThrows(BusinessException.class, () -> service.requireActive(" "));
        assertEquals("OTHER", service.requireActive("other").getCode());
    }

    @Test
    void deleteOnlySwitchesOffAReasonUsedByTransfers() {
        TransferReason used = reason("USED", true);
        TransferReason unused = reason("UNUSED", true);
        when(transferReasonRepository.findById("id-USED")).thenReturn(Optional.of(used));
        when(transferReasonRepository.findById("id-UNUSED")).thenReturn(Optional.of(unused));
        when(tokenRepository.existsByTransferReason("USED")).thenReturn(true);
        when(tokenRepository.existsByTransferReason("UNUSED")).thenReturn(false);

        assertFalse(service.delete("id-USED"));
        assertFalse(used.getActive());
        verify(transferReasonRepository).save(used);

        assertTrue(service.delete("id-UNUSED"));
        verify(transferReasonRepository).delete(unused);
    }
}
