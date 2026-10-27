package com.gis.servelq.services;

import com.gis.servelq.Exceptions.ResourceNotFoundException;
import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * A single attempt at issuing a token, in its own transaction.
 *
 * This lives in its own bean deliberately. It used to be a private method on
 * TokenService called as this.createTokenOnce(...), so Spring's proxy never saw
 * it and its @Transactional did nothing - every "retry" ran inside the same
 * outer transaction, which by then was already marked rollback-only. With
 * REQUIRES_NEW on a separate bean each attempt really is a fresh transaction,
 * so retrying after a unique-constraint violation can actually succeed.
 */
@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final CategoryService categoryService;
    private final CounterServiceLinker counterServiceLinker;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Token issueOnce(TokenRequest request) {
        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        LocalDate today = LocalDate.now();
        int priority = request.getPriority() != null ? request.getPriority() : 50;

        // Each prefix has its own daily sequence: A001, B001, A002, C001.
        String prefix = resolveTokenPrefix(mainServiceCode(service), priority);
        int nextSeq = tokenRepository.findLastSeqForPrefix(branch.getId(), prefix, today)
                .orElse(0) + 1;

        Token token = new Token();
        token.setToken(prefix + String.format("%03d", nextSeq));
        token.setTokenPrefix(prefix);
        token.setTokenSeq(nextSeq);
        token.setTokenDate(today);
        token.setPriority(priority);

        token.setBranchId(branch.getId());
        token.setServiceId(service.getId());
        token.setServiceName(service.getName());
        token.setServiceArabicName(service.getArabicName());

        token.setStatus(TokenStatus.WAITING);
        token.setCreatedByUserId(currentUserId());
        // No number (paper ticket) is stored as null rather than "".
        String mobile = request.getMobileNumber();
        token.setMobileNumber(mobile == null || mobile.isBlank() ? null : mobile.trim());
        List<String> counterIds = counterServiceLinker.countersForService(service);
        token.setCounterIds(counterIds.isEmpty() ? null : counterIds);

        // saveAndFlush so a duplicate sequence surfaces here, inside this
        // transaction, instead of at commit time where the caller cannot retry.
        return tokenRepository.saveAndFlush(token);
    }

    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? user.id() : null;
    }

    /**
     * Sub-services take their main service's letter (A, B, ...), so a ticket
     * for a sub-service of "A" is A0xx rather than the sub-service's own code.
     */
    private String mainServiceCode(Services service) {
        Services current = service;
        // Bounded walk up the parent chain, in case of bad data (a cycle).
        for (int depth = 0; depth < 10 && current.getParentId() != null; depth++) {
            Services parent = serviceRepository.findById(current.getParentId()).orElse(null);
            if (parent == null) break;
            current = parent;
        }
        return current.getCode();
    }

    private String resolveTokenPrefix(String serviceCode, int priority) {
        if (priority == 50) {
            return serviceCode.trim().toUpperCase();
        }
        return categoryService.findByPriority(priority)
                .map(c -> c.getCode().trim().toUpperCase())
                .orElse(serviceCode.trim().toUpperCase());
    }
}
