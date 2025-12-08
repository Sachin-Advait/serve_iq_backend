package com.gis.servelq.services;

import com.gis.servelq.dto.TokenResponseDTO;
import com.gis.servelq.models.Token;
import com.gis.servelq.models.TokenStatus;
import com.gis.servelq.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TokenRepository tokenRepository;

    // Completed tokens (serving report) with optional filters
    public List<TokenResponseDTO> getServingReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(t -> t.getStatus() == TokenStatus.DONE)
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Waiting tokens with optional filters
    public List<TokenResponseDTO> getWaitingReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(t -> t.getStatus() == TokenStatus.WAITING)
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }

    // Transferred tokens report
    public List<TokenResponseDTO> getTransferredTokensReport(String branchId, String counterId, String serviceId, LocalDate date) {
        return tokenRepository.findByBranchId(branchId)
                .stream()
                .filter(Token::getIsTransfer) // Only transferred tokens
                .filter(t -> counterId == null || counterId.isEmpty() || counterId.equals(t.getAssignedCounterId()))
                .filter(t -> serviceId == null || serviceId.isEmpty() || serviceId.equals(t.getServiceId()))
                .filter(t -> date == null || isSameDate(t.getCreatedAt(), date))
                .map(TokenResponseDTO::fromEntity)
                .toList();
    }
    
    // Helper method to check if token's createdAt is on the same date
    private boolean isSameDate(LocalDateTime tokenTime, LocalDate filterDate) {
        if (tokenTime == null) return false;
        LocalDate tokenDate = tokenTime.toLocalDate();
        return tokenDate.equals(filterDate);
    }
}
