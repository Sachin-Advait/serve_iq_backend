package com.gis.servelq.services;

import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponse;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.ServiceModel;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final TokenRepository tokenRepository;
    private final ServiceRepository serviceRepository;
    private final BranchRepository branchRepository;
    private final SocketService socketService;

    private static TokenResponse getTokenResponse(Token savedToken, ServiceModel serviceModel, long waitingCount) {
        TokenResponse response = new TokenResponse();
        response.setId(savedToken.getId());
        response.setToken(savedToken.getToken());
        response.setServiceName(serviceModel.getName());
        response.setServiceCode(serviceModel.getCode());
        response.setStatus(savedToken.getStatus());
        response.setWaitingCount((int) waitingCount);
        response.setCreatedAt(savedToken.getCreatedAt());

        // Calculate estimated time (simplified)
        if (serviceModel.getSlaSec() != null) {
            response.setEstimatedTime(
                    savedToken.getCreatedAt().plusSeconds(serviceModel.getSlaSec() * waitingCount)
            );
        }
        return response;
    }

    @Transactional
    public TokenResponse generateToken(TokenRequest request) {
        // Validate service and branch
        ServiceModel serviceModel = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        // Generate token number
        String tokenNumber = generateTokenNumber(branch);

        // Create token
        Token token = new Token();
        token.setToken(tokenNumber);
        token.setBranchId(branch.getId());
        token.setServiceId(serviceModel.getId());
        token.setPriority(request.getPriority());
        token.setStatus(TokenStatus.WAITING);
        if (request.getCounterId() != null) token.setCounterId(request.getCounterId());
        token.setCivilId(request.getCivilId());

        Token savedToken = tokenRepository.save(token);

        long waitingCount = tokenRepository.countByServiceIdAndStatus(serviceModel.getId(), TokenStatus.WAITING);

        if (request.getCounterId() != null) socketService.agentUpcoming(request.getCounterId());
        return getTokenResponse(savedToken, serviceModel, waitingCount);
    }

    public Token getTokenById(String tokenId) {
        return tokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token not found"));
    }

    private String generateTokenNumber(Branch branch) {
        Optional<Integer> lastNumber = tokenRepository.findLastTokenNumber(branch.getId());
        int nextNumber = lastNumber.orElse(1000) + 1;
        return String.valueOf(nextNumber);
    }
}
