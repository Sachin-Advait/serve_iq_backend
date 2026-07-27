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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Token issueOnce(TokenRequest request) {
        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        LocalDate today = LocalDate.now();
        int priority = request.getPriority() != null ? request.getPriority() : 50;

        int nextSeq = tokenRepository.findLastSeqForPriorityToday(
                branch.getId(),
                priority,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        ).orElse(0) + 1;

        Token token = new Token();
        token.setToken(resolveTokenPrefix(service.getCode(), priority) + String.format("%03d", nextSeq));
        token.setTokenSeq(nextSeq);
        token.setTokenDate(today);
        token.setPriority(priority);

        token.setBranchId(branch.getId());
        token.setServiceId(service.getId());
        token.setServiceName(service.getName());

        token.setStatus(TokenStatus.WAITING);
        token.setMobileNumber(request.getMobileNumber());
        token.setCounterIds(service.getCounterIds());

        // saveAndFlush so a duplicate sequence surfaces here, inside this
        // transaction, instead of at commit time where the caller cannot retry.
        return tokenRepository.saveAndFlush(token);
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
