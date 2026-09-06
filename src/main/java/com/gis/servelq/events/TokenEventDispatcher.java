package com.gis.servelq.events;

import com.gis.servelq.services.AgentService;
import com.gis.servelq.services.CounterService;
import com.gis.servelq.services.SocketService;
import com.gis.servelq.services.TvContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class TokenEventDispatcher {

    private final SocketService socketService;
    private final AgentService agentService;
    private final CounterService counterService;
    private final TvContentService tvContentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvent(TokenEvent event) {

        switch (event.getType()) {

            case TOKEN_CREATED -> {
                socketService.notifyBranchTV(event.getBranchId());
            }

            case TOKEN_CALLED -> {
                socketService.notifyBranchTV(event.getBranchId());
                socketService.notifyCounterDisplay(
                        event.getCounterId(),
                        counterService.getCounterStatusDetails(event.getCounterId())
                );
                socketService.notifyMeetingTV(event.getBranchId());
            }

            case TOKEN_SERVING_STARTED,
                 TOKEN_COMPLETED,
                 TOKEN_TRANSFERRED,
                 TOKEN_HELD,
                 TOKEN_NO_SHOW -> {
                socketService.notifyBranchTV(event.getBranchId());
                socketService.notifyMeetingTV(event.getBranchId());

                var data = counterService.getCounterStatusDetails(event.getCounterId());
                socketService.notifyCounterDisplay(event.getCounterId(), data);

                var agentData = agentService.getUpcomingTokensForCounter(event.getCounterId());
                socketService.notifyAgentQueue(event.getCounterId(), agentData);
            }

            case AGENT_QUEUE_CHANGED -> {
                var data = agentService.getUpcomingTokensForCounter(event.getCounterId());
                socketService.notifyAgentQueue(event.getCounterId(), data);
            }

            case COUNTER_STATUS_CHANGED -> {
                var data = counterService.getCounterStatusDetails(event.getCounterId());
                socketService.notifyCounterDisplay(event.getCounterId(), data);
                socketService.notifyMeetingTV(event.getBranchId());
            }

            case FEEDBACK_SUBMITTED -> {
                var counterData = counterService.getCounterStatusDetails(event.getCounterId());
                socketService.notifyCounterDisplay(event.getCounterId(), counterData);
                socketService.notifyCounter(event.getCounterId(), counterData);
            }

            default -> {
            }
        }
    }
}
