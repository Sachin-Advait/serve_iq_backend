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

        // Get latest called tokens (last 2)
        List<Token> latestCalled = tokenRepository.findLatestCalledTokens(branchId)
                .stream().limit(4).toList();

        // Get currently serving tokens
        List<Token> nowServing = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.SERVING);

        // Get upcoming tokens (first 10)
        List<Token> upcoming = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                        branchId, TokenStatus.WAITING)
                .stream().limit(10).toList();

        TVDisplayResponseDTO response = new TVDisplayResponseDTO();

        response.setBranchName(branch.getName());

        // Map latest calls
        response.setLatestCalls(latestCalled.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        // Map now serving
        response.setNowServing(nowServing.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        // Map upcoming tokens
        response.setUpcomingTokens(upcoming.stream()
                .map(Token::getToken)
                .collect(Collectors.toList()));

        return response;
    }
}