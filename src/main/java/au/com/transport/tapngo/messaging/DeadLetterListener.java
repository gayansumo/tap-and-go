package au.com.transport.tapngo.messaging;

import au.com.transport.tapngo.domain.FailedEvent;
import au.com.transport.tapngo.repository.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes messages from the Dead Letter Queue after max retries exceeded.
 * Saves them to failed_events for support investigation and replay.
 *
 * ActiveMQ automatically moves messages to ActiveMQ.DLQ after
 * the redelivery policy maximum is exhausted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterListener {

    private final FailedEventRepository failedEventRepository;

    @JmsListener(destination = "${app.messaging.queues.dead-letter}")
    @Transactional
    public void onDeadLetter(Long tapEventId) {
        log.error("Message arrived in DLQ after max retries: tapEventId={}", tapEventId);

        FailedEvent failedEvent = FailedEvent.builder()
            .tapEventId(tapEventId)
            .failureType(FailedEvent.FailureType.MAX_RETRIES_EXCEEDED)
            .failureReason("Message exhausted all retry attempts and was moved to DLQ. " +
                "tapEventId=" + tapEventId)
            .rawPayload(String.valueOf(tapEventId))
            .build();

        failedEventRepository.save(failedEvent);

        log.error("DLQ event persisted for support review: tapEventId={}", tapEventId);
    }
}
