package com.gis.servelq.controllers;

import com.gis.servelq.models.*;
import com.gis.servelq.repository.BranchRepository;
import com.gis.servelq.repository.CounterRepository;
import com.gis.servelq.repository.TokenRepository;
import com.gis.servelq.repository.UserRepository;
import com.gis.servelq.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent logout must release the counter, and a late feedback for the previous visitor
 * must not make a counter that is already calling the next token look IDLE (the agent app
 * only allows logout from IDLE/PAUSED, so the backend then refused the logout).
 */
@SpringBootTest(properties = {
        "ad.ldap.urls=ldap://localhost:1",
        "ad.ldap.bind-user=test",
        "ad.ldap.bind-password=test",
        "ad.ldap.user-search-base=dc=test",
        "ad.ldap.user-search-filter=(uid={0})",
        "image.upload.dir=target/test-images"
})
@AutoConfigureMockMvc
class AgentLogoutTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CounterRepository counters;
    @Autowired BranchRepository branches;
    @Autowired TokenRepository tokens;
    @Autowired JwtService jwt;
    @Autowired com.gis.servelq.services.CounterService counterService;

    @Test
    void logoutReleasesCounter() throws Exception {
        Fixture f = agentAtCounter();

        mvc.perform(post("/serveiq/api/agent/counter/logout").header("Authorization", "Bearer " + f.jwt))
                .andExpect(status().isNoContent());

        assertThat(counters.findById(f.counter.getId()).orElseThrow().getUserId()).isNull();
    }

    @Test
    void lateFeedbackDoesNotIdleACounterCallingTheNextToken() throws Exception {
        Fixture f = agentAtCounter();
        Token previous = token(f, "A001", TokenStatus.REVIEW);
        token(f, "A002", TokenStatus.CALLING);
        Counter c = counters.findById(f.counter.getId()).orElseThrow();
        c.setStatus(CounterStatus.CALLING);
        counters.save(c);

        mvc.perform(post("/serveiq/api/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\":\"" + previous.getId() + "\",\"counterCode\":\"" + c.getCode()
                                + "\",\"rating\":\"HAPPY\"}"))
                .andExpect(status().isOk());

        assertThat(counters.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CounterStatus.CALLING);
        assertThat(tokens.findById(previous.getId()).orElseThrow().getStatus()).isEqualTo(TokenStatus.DONE);
    }

    @Test
    void feedbackIdlesACompletedCounter() throws Exception {
        Fixture f = agentAtCounter();
        Token previous = token(f, "A003", TokenStatus.REVIEW);
        Counter c = counters.findById(f.counter.getId()).orElseThrow();
        c.setStatus(CounterStatus.COMPLETE);
        counters.save(c);

        mvc.perform(post("/serveiq/api/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenId\":\"" + previous.getId() + "\",\"counterCode\":\"" + c.getCode()
                                + "\",\"rating\":\"HAPPY\"}"))
                .andExpect(status().isOk());

        assertThat(counters.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CounterStatus.IDLE);
    }

    @Test
    void loginToACounterLeftWithACallingTokenDoesNotShowIdle() throws Exception {
        Fixture f = agentAtCounter();
        Counter c = counters.findById(f.counter.getId()).orElseThrow();
        c.setUserId(null);
        c.setStatus(CounterStatus.CLOSED);
        counters.save(c);
        token(f, "A004", TokenStatus.CALLING);

        var actor = new com.gis.servelq.security.AuthenticatedUser(f.agent.getId(), f.agent.getEmail(),
                "USER", f.branch.getId(), null);
        counterService.claimCounter(c.getId(), false, actor);

        assertThat(counters.findById(c.getId()).orElseThrow().getStatus()).isEqualTo(CounterStatus.CALLING);
    }

    private record Fixture(Branch branch, Counter counter, User agent, String jwt) {
    }

    private Fixture agentAtCounter() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Branch b = new Branch();
        b.setCode("B" + suffix);
        b.setName("Branch");
        b = branches.save(b);

        Counter c = new Counter();
        c.setCode("C" + suffix);
        c.setName("Counter " + suffix);
        c.setBranchId(b.getId());
        c.setStatus(CounterStatus.IDLE);
        c = counters.save(c);

        User u = new User();
        u.setEmail(suffix + "@test.local");
        u.setName("Agent " + suffix);
        u.setPassword("x");
        u.setRole(UserRole.USER);
        u.setBranchId(b.getId());
        u = users.save(u);

        c.setUserId(u.getId());
        c = counters.save(c);
        u.setCounterId(c.getId());
        u = users.save(u);
        return new Fixture(b, c, u, jwt.generateToken(u));
    }

    private Token token(Fixture f, String number, TokenStatus status) {
        Token t = new Token();
        t.setToken(number + UUID.randomUUID().toString().substring(0, 4));
        t.setTokenSeq((int) (Math.random() * 100000));
        t.setTokenDate(LocalDate.now());
        t.setBranchId(f.branch.getId());
        t.setServiceId("svc");
        t.setServiceName("Service");
        t.setMobileNumber("99999999");
        t.setStatus(status);
        t.setAssignedCounterId(f.counter.getId());
        t.setAssignedCounterName(f.counter.getName());
        return tokens.save(t);
    }
}
