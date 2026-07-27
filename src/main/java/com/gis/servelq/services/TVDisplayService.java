package com.gis.servelq.services;

import com.gis.servelq.dto.TVDisplayResponseDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TVDisplayService {

    /** How many rows the board shows per list. */
    private static final int MAX_LIST = 10;
    private static final int MAX_SERVING = 20;

    private final TokenRepository tokenRepository;
    private final BranchRepository branchRepository;

    public TVDisplayResponseDTO getTVDisplayData(String branchId) {

        Branch branch = branchRepository.findByIdAndEnabledTrue(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found or disabled"));

        // This whole snapshot is rebuilt and broadcast on every token event -
        // created, called, serving, completed, transferred, held, no-show - so
        // it is the busiest read in the system. It used to be nine round trips:
        // the branch, the called list, three unbounded status lists that were
        // then trimmed in Java, and four separate COUNT queries. It is now the
        // branch, the lists with the limits pushed into the database, and one
        // grouped count.
        List<Token> latestCalled = tokenRepository.findLatestCalledTokens(
                branchId, PageRequest.of(0, 4));

        List<Token> nowServing = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.SERVING, PageRequest.of(0, MAX_SERVING));

        List<Token> upcoming = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.WAITING, PageRequest.of(0, MAX_LIST));

        List<Token> hold = tokenRepository.findByBranchIdAndStatusOrderByPriorityAscCreatedAtAsc(
                branchId, TokenStatus.HOLD, PageRequest.of(0, MAX_LIST));

        Map<TokenStatus, Long> counts = new EnumMap<>(TokenStatus.class);
        for (Object[] row : tokenRepository.countByStatusForBranch(branchId)) {
            counts.put((TokenStatus) row[0], (Long) row[1]);
        }

        long waitingCount = counts.getOrDefault(TokenStatus.WAITING, 0L);
        long servingCount = counts.getOrDefault(TokenStatus.SERVING, 0L);
        long noShowCount = counts.getOrDefault(TokenStatus.NO_SHOW, 0L);
        long completedCount = counts.getOrDefault(TokenStatus.DONE, 0L);

        TVDisplayResponseDTO response = new TVDisplayResponseDTO();
        response.setBranchName(branch.getName());

        response.setLatestCalls(latestCalled.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        response.setNowServing(nowServing.stream().map(token -> {
            TVDisplayResponseDTO.DisplayToken dt = new TVDisplayResponseDTO.DisplayToken();
            dt.setToken(token.getToken());
            dt.setCounter(token.getAssignedCounterName());
            dt.setService(token.getServiceName());
            return dt;
        }).collect(Collectors.toList()));

        response.setUpcomingTokens(upcoming.stream()
                .map(Token::getToken)
                .collect(Collectors.toList()));

        response.setHoldTokens(hold.stream()
                .map(Token::getToken)
                .collect(Collectors.toList()));

        response.setWaiting(waitingCount);
        response.setServing(servingCount);
        response.setNoShow(noShowCount);
        response.setServedToday(completedCount);

        return response;
    }
}
