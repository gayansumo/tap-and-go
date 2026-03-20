package au.com.transport.tapngo.messaging;

import au.com.transport.tapngo.domain.TapEvent;

import java.util.List;

/**
 * Publishes tap events to the message queue.
 * Abstracted behind an interface so JMS can be swapped to SQS
 * without touching the ingestion service.
 */
public interface TapEventPublisher {

    /**
     * Publish a batch of tap events in a single operation.
     * Implementations should guarantee at-least-once delivery.
     */
    void publishBatch(List<TapEvent> tapEvents);
}
