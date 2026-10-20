package com.gis.servelq.services;

import com.gis.servelq.Exceptions.BusinessException;
import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.TransferReasonRequest;
import com.gis.servelq.models.TransferReason;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.TransferReasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferReasonService {

    private final TransferReasonRepository transferReasonRepository;
    private final TokenRepository tokenRepository;

    public List<TransferReason> findAll(boolean includeInactive) {
        return includeInactive
                ? transferReasonRepository.findAllByOrderBySortOrderAscTitleEnAsc()
                : transferReasonRepository.findByActiveTrueOrderBySortOrderAscTitleEnAsc();
    }

    public Optional<TransferReason> findByCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return transferReasonRepository.findByCode(code.trim().toUpperCase(Locale.ROOT));
    }

    /** The reason an agent picked, which must exist and be active. */
    public TransferReason requireActive(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Select a reason for the transfer");
        }
        return findByCode(code)
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .orElseThrow(() -> new BusinessException("Unknown transfer reason: " + code));
    }

    @Transactional
    public TransferReason create(TransferReasonRequest request) {
        TransferReason reason = new TransferReason();
        reason.setTitleEn(required(request.getTitleEn(), "English title"));
        reason.setTitleAr(required(request.getTitleAr(), "Arabic title"));
        reason.setCode(uniqueCode(reason.getTitleEn()));
        reason.setActive(request.getActive() == null || request.getActive());
        reason.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder());
        return transferReasonRepository.save(reason);
    }

    @Transactional
    public TransferReason update(String id, TransferReasonRequest request) {
        TransferReason reason = transferReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer reason not found"));
        if (request.getTitleEn() != null) reason.setTitleEn(required(request.getTitleEn(), "English title"));
        if (request.getTitleAr() != null) reason.setTitleAr(required(request.getTitleAr(), "Arabic title"));
        if (request.getActive() != null) reason.setActive(request.getActive());
        if (request.getSortOrder() != null) reason.setSortOrder(request.getSortOrder());
        return transferReasonRepository.save(reason);
    }

    /**
     * Removes a reason no transfer has used yet. A used one is only switched
     * off, so reports still show its title for the old transfers.
     *
     * @return true when the reason was removed, false when it was deactivated
     */
    @Transactional
    public boolean delete(String id) {
        TransferReason reason = transferReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer reason not found"));
        if (tokenRepository.existsByTransferReason(reason.getCode())) {
            reason.setActive(false);
            transferReasonRepository.save(reason);
            return false;
        }
        transferReasonRepository.delete(reason);
        return true;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new BusinessException(field + " must be 255 characters or fewer");
        }
        return trimmed;
    }

    /** WRONG_SERVICE style code from the English title, made unique. */
    String uniqueCode(String titleEn) {
        String base = titleEn.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "REASON";
        if (base.length() > 40) base = base.substring(0, 40).replaceAll("_+$", "");
        String code = base;
        for (int i = 2; transferReasonRepository.existsByCode(code); i++) {
            code = base + "_" + i;
        }
        return code;
    }

    private int nextSortOrder() {
        return transferReasonRepository.findAll().stream()
                .mapToInt(r -> r.getSortOrder() != null ? r.getSortOrder() : 0)
                .max().orElse(0) + 1;
    }
}
