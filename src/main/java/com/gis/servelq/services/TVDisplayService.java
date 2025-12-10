package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TVDisplayService {
    private final TokenRepository tokenRepository;
    private final BranchRepository branchRepository;

    public TVDisplayResponseDTO getTVDisplayData(String branchId) {

        Branch branch = branchRepository.findByIdAndEnabledTrue(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found or disabled"));

        // Get latest called tokens (last 4)
        List<Token> latestCalled = tokenRepository.findLatestCalledTokens(branchId)
                .stream().limit(4).toList();

        // Get currently serving tokens
        List<Token> nowServing = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.SERVING);

        // Get upcoming tokens
        List<Token> upcoming = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                        branchId, TokenStatus.WAITING)
                .stream().limit(10).toList();

        // ✔ NEW — statistics
        long waitingCount = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.WAITING);
        long servingCount = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.SERVING);
        long noShowCount = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.NO_SHOW);
        long completedCount = tokenRepository.countByBranchIdAndStatus(branchId, TokenStatus.DONE);

        TVDisplayResponseDTO response = new TVDisplayResponseDTO();
        response.setBranchName(branch.getName());

        // latest calls
        response.setLatestCalls(latestCalled.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        // serving list with counter + service
        response.setNowServing(nowServing.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        // upcoming only tokens
        response.setUpcomingTokens(upcoming.stream()
                .map(Token::getToken)
                .collect(Collectors.toList()));

        // ✔ NEW — set statistics
        response.setWaiting(waitingCount);
        response.setServing(servingCount);
        response.setNoShow(noShowCount);
        response.setServedToday(completedCount);

        return response;
    }
}
