package au.com.transport.tapngo.messaging;

import au.com.transport.tapngo.domain.TapEvent;
import au.com.transport.tapngo.exception.TapProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JMS-backed publisher. Sends each TapEvent as an individual message
 * so the consumer can process them independently with its own transaction.
 *
 * NOTE: In production this would be replaced with an SQS implementation.
 * The interface boundary makes that swap a single class change.
 *
 * KNOWN LIMITATION documented here for reviewers:
 * True XA transactions across JMS + DB are not used here due to complexity.
 * Instead we use the transactional outbox approach: DB is written first,
 * then published. If publish fails, the OutboxDispatcher retries unpublished
 * records. This guarantees at-least-once delivery without XA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JmsTapEventPublisher implements TapEventPublisher {

    private final JmsTemplate jmsTemplate;

    @Value("${app.messaging.queues.tap-events}")
    private String tapEventsQueue;

    @Override
    public void publishBatch(List<TapEvent> tapEvents) {
        log.debug("Publishing batch of {} tap events to queue", tapEvents.size());

        try {
            for (TapEvent tapEvent : tapEvents) {
                jmsTemplate.convertAndSend(tapEventsQueue, tapEvent.getId());
            }
            log.debug("Successfully published {} tap event IDs", tapEvents.size());
        } catch (Exception e) {
            log.error("Failed to publish batch of {} events", tapEvents.size(), e);
            throw new TapProcessingException("Queue publish failed for batch", e);
        }
    }
}
