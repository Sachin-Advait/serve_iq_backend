package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponse;
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

    public TVDisplayResponse getTVDisplayData(String branchId) {
        // Validate branch
        branchRepository.findByIdAndEnabledTrue(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found or disabled"));

        // Get latest called tokens (last 2)
        List<Token> latestCalled = tokenRepository.findLatestCalledTokens(branchId)
                .stream().limit(4).toList();

        // Get currently serving tokens
        List<Token> nowServing = tokenRepository.findByCounterIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.SERVING);

        // Get upcoming tokens (first 10)
        List<Token> upcoming = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                        branchId, TokenStatus.WAITING)
                .stream().limit(10).collect(Collectors.toList());

        TVDisplayResponse response = new TVDisplayResponse();

        // Map latest calls
        response.setLatestCalls(latestCalled.stream().map(token -> {
            TVDisplayResponse.DisplayToken dt = new TVDisplayResponse.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getCounter() != null ? token.getCounter().getName() : "N/A");
            dt.setService(token.getService().getName());
            dt.setCalledAt(token.getCalledAt());
            return dt;
        }).collect(Collectors.toList()));

        // Map now serving
        response.setNowServing(nowServing.stream().map(token -> {
            TVDisplayResponse.DisplayToken dt = new TVDisplayResponse.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getCounter() != null ? token.getCounter().getName() : "N/A");
            dt.setService(token.getService().getName());
            dt.setCalledAt(token.getCalledAt());
            return dt;
        }).collect(Collectors.toList()));

        // Map upcoming tokens
        response.setUpcomingTokens(upcoming.stream()
                .map(Token::getToken)
                .collect(Collectors.toList()));

        return response;
    }
}