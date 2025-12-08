package com.gis.servelq.services;

import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Services;
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

    @Transactional
    public TokenResponseDTO generateToken(TokenRequest request) {
        Services serviceModel = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        String tokenNumber = generateTokenNumber(branch);

        Token token = new Token();
        token.setToken(tokenNumber);
        token.setBranchId(branch.getId());
        token.setServiceId(serviceModel.getId());
        token.setServiceName(serviceModel.getName());

        token.setPriority(request.getPriority());
        token.setStatus(TokenStatus.WAITING);
        token.setMobileNumber(request.getMobileNumber());

        if (request.getCounterIds() != null) token.setCounterId(request.getCounterIds());

        Token savedToken = tokenRepository.save(token);

        long waitingCount = tokenRepository.countByServiceIdAndStatus(
                serviceModel.getId(),
                TokenStatus.WAITING
        );
        socketService.agentUpcoming(request.getCounterIds());
        socketService.tvSocket(request.getBranchId());

        return TokenResponseDTO.fromEntity(savedToken);
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
