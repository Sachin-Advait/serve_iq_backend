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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        mvc.perform(post("/serveiq/api/feedback").header("Authorization", "Bearer " + feedbackScreenJwt(f))
                        .contentType(MediaType.APPLICATION_JSON)
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

        mvc.perform(post("/serveiq/api/feedback").header("Authorization", "Bearer " + feedbackScreenJwt(f))
                        .contentType(MediaType.APPLICATION_JSON)
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

    @Test
    void loginAtAnotherCounterReleasesTheFirstAndSignsOutTheOldApp() throws Exception {
        Fixture f = agentAtCounter();
        Counter second = counter(f.branch);
        // users.counter_id has drifted (e.g. an admin edit): it no longer names the held counter.
        User agent = users.findById(f.agent.getId()).orElseThrow();
        agent.setCounterId(null);
        users.save(agent);

        Thread.sleep(1100); // the old JWT must be from an earlier second than the cutoff
        var actor = new com.gis.servelq.security.AuthenticatedUser(f.agent.getId(), f.agent.getEmail(),
                "USER", f.branch.getId(), null);
        counterService.claimCounter(second.getId(), false, actor);

        assertThat(counters.findById(f.counter.getId()).orElseThrow().getUserId()).isNull();
        assertThat(counters.findById(second.getId()).orElseThrow().getUserId()).isEqualTo(f.agent.getId());
        assertThat(counters.findByUserId(f.agent.getId())).hasSize(1);
        mvc.perform(post("/serveiq/api/agent/counter/logout").header("Authorization", "Bearer " + f.jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void reloginAtTheSameCounterKeepsTheOtherDeviceSignedIn() throws Exception {
        Fixture f = agentAtCounter();
        var actor = new com.gis.servelq.security.AuthenticatedUser(f.agent.getId(), f.agent.getEmail(),
                "USER", f.branch.getId(), f.counter.getId());
        counterService.claimCounter(f.counter.getId(), false, actor);

        assertThat(users.findById(f.agent.getId()).orElseThrow().getTokensValidAfter()).isNull();
    }

    @Test
    void paperTicketWithoutMobileNumberSaves() {
        Fixture f = agentAtCounter();
        Token t = token(f, "A005", TokenStatus.WAITING);
        t.setMobileNumber(null);
        tokens.saveAndFlush(t);

        assertThat(tokens.findById(t.getId()).orElseThrow().getMobileNumber()).isNull();
    }

    @Test
    void anonymousCallersCannotIssueTokensSendFeedbackOrReadUsers() throws Exception {
        mvc.perform(post("/serveiq/api/tokens/generate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/serveiq/api/feedback").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/serveiq/api/users"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void agentsCannotReadOrDeleteOtherUsers() throws Exception {
        Fixture f = agentAtCounter();
        mvc.perform(get("/serveiq/api/users").header("Authorization", "Bearer " + f.jwt))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/serveiq/api/users/" + f.agent.getId()).header("Authorization", "Bearer " + f.jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void agentAppCanSaveItsOwnFcmToken() throws Exception {
        Fixture f = agentAtCounter();
        mvc.perform(post("/serveiq/api/users/update-fcm-token/").header("Authorization", "Bearer " + f.jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"someone-else\",\"fcmToken\":\"fcm-1\"}"))
                .andExpect(status().isOk());

        assertThat(users.findById(f.agent.getId()).orElseThrow().getFcmToken()).isEqualTo("fcm-1");
    }

    /** The feedback screen logs in as a FEEDBACK user fixed to the counter. */
    private String feedbackScreenJwt(Fixture f) {
        User u = new User();
        u.setEmail("fb-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local");
        u.setName("Feedback screen");
        u.setPassword("x");
        u.setRole(UserRole.FEEDBACK);
        u.setBranchId(f.branch.getId());
        u.setCounterId(f.counter.getId());
        return jwt.generateToken(users.save(u));
    }

    private Counter counter(Branch b) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Counter c = new Counter();
        c.setCode("C" + suffix);
        c.setName("Counter " + suffix);
        c.setBranchId(b.getId());
        c.setStatus(CounterStatus.CLOSED);
        c.setEnabled(true);
        return counters.save(c);
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
