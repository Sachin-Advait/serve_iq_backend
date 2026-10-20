package com.gis.servelq.services;

import com.gis.servelq.dto.TokenRequest;
import com.gis.servelq.models.Branch;
import com.gis.servelq.models.Services;
import com.gis.servelq.models.Token;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.ServiceRepository;
import com.gis.servelq.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenIssuerPrefixTest {

    @Mock private TokenRepository tokenRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private CategoryService categoryService;
    @Mock private CounterServiceLinker counterServiceLinker;
    @InjectMocks private TokenIssuer tokenIssuer;

    @Test
    void subServiceTicketUsesMainServiceLetter() {
        Services main = new Services();
        main.setId("main");
        main.setCode("A");
        Services sub = new Services();
        sub.setId("sub");
        sub.setCode("X");
        sub.setParentId("main");
        Branch branch = new Branch();
        branch.setId("b1");

        when(serviceRepository.findById("sub")).thenReturn(Optional.of(sub));
        when(serviceRepository.findById("main")).thenReturn(Optional.of(main));
        when(branchRepository.findById("b1")).thenReturn(Optional.of(branch));
        when(tokenRepository.findLastSeqForPrefix(eq("b1"), eq("A"), any()))
                .thenReturn(Optional.of(4));
        when(counterServiceLinker.countersForService(sub)).thenReturn(List.of());
        when(tokenRepository.saveAndFlush(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest request = new TokenRequest();
        request.setServiceId("sub");
        request.setBranchId("b1");
        request.setPriority(50);

        assertThat(tokenIssuer.issueOnce(request).getToken()).isEqualTo("A005");
    }

    @Test
    void eachPrefixCountsOnItsOwn() {
        Services a = new Services();
        a.setId("a");
        a.setCode("A");
        Services b = new Services();
        b.setId("b");
        b.setCode("b ");
        Branch branch = new Branch();
        branch.setId("b1");

        when(serviceRepository.findById("a")).thenReturn(Optional.of(a));
        when(serviceRepository.findById("b")).thenReturn(Optional.of(b));
        when(branchRepository.findById("b1")).thenReturn(Optional.of(branch));
        when(tokenRepository.findLastSeqForPrefix(eq("b1"), eq("A"), any())).thenReturn(Optional.of(7));
        when(tokenRepository.findLastSeqForPrefix(eq("b1"), eq("B"), any())).thenReturn(Optional.empty());
        when(counterServiceLinker.countersForService(any())).thenReturn(List.of());
        when(tokenRepository.saveAndFlush(any(Token.class))).thenAnswer(i -> i.getArgument(0));

        TokenRequest forA = new TokenRequest();
        forA.setServiceId("a");
        forA.setBranchId("b1");
        forA.setPriority(50);
        TokenRequest forB = new TokenRequest();
        forB.setServiceId("b");
        forB.setBranchId("b1");
        forB.setPriority(50);

        Token tokenA = tokenIssuer.issueOnce(forA);
        Token tokenB = tokenIssuer.issueOnce(forB);

        assertThat(tokenA.getToken()).isEqualTo("A008");
        assertThat(tokenA.getTokenPrefix()).isEqualTo("A");
        assertThat(tokenB.getToken()).isEqualTo("B001");
        assertThat(tokenB.getTokenPrefix()).isEqualTo("B");
    }
}
