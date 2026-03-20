package au.com.transport.tapngo.messaging;

import au.com.transport.tapngo.repository.TapEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JMS consumer for tap events.
 * Picks up tapEventId from queue, loads full event from DB,
 * runs through state machine, then fare calculator.
 *
 * Transaction boundary:
 * The entire method is @Transactional. If anything fails:
 * - DB changes roll back
 * - JMS does NOT acknowledge the message
 * - ActiveMQ redelivers after backoff delay
 * - After max retries → message goes to DLQ
 * - DeadLetterListener saves to failed_events table
 *
 * Concurrency:
 * Multiple instances of this listener run in parallel (configured
 * in JmsConfig concurrency="3-10"). Each has its own transaction.
 * The pessimistic lock on CustomerDailyTotal prevents concurrent
 * overcharge for the same card.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TapEventListener {

    private final TapEventRepository tapEventRepository;
//    private final TripStateMachine tripStateMachine;
//    private final FareCalculator fareCalculator;

    @JmsListener(
        destination = "${app.messaging.queues.tap-events}",
        containerFactory = "jmsListenerContainerFactory"
    )
    @Transactional
    public void onMessage(Long tapEventId) {
        log.debug("Message received: tapEventId={}", tapEventId);

//        TapEvent tapEvent = tapEventRepository.findById(tapEventId)
//            .orElseThrow(() -> new NonRetryableException(
//                "TapEvent not found in DB: id=" + tapEventId
//            ));
//
//        try {
//            processEvent(tapEvent);
//        } catch (NonRetryableException e) {
//            // Bad data — do not redeliver, let it go to DLQ
//            log.error("Non-retryable error processing tapEventId={}: {}",
//                tapEventId, e.getMessage());
//            throw e;
//        } catch (Exception e) {
//            // Transient failure — redeliver via JMS retry policy
//            log.warn("Retryable error processing tapEventId={}, will retry: {}",
//                tapEventId, e.getMessage());
//            throw new TapProcessingException("Processing failed for tapEventId=" + tapEventId, e);
//        }
    }

//    private void processEvent(TapEvent tapEvent) {
//        TripStateMachine.Result result = switch (tapEvent.getTapType()) {
//            case ON  -> tripStateMachine.processTapOn(tapEvent);
//            case OFF -> tripStateMachine.processTapOff(tapEvent);
//        };
//
//        if (result.isUnmatched()) {
//            // Unmatched events already handled inside state machine
//            return;
//        }
//
//        // If a force-closed trip exists (second TAP ON scenario),
//        // run fare calculator on it first
//        if (result.hasForceClosedTrip()) {
//            fareCalculator.calculateAndApply(result.forceClosedTrip());
//        }
//
//        // Run fare calculator on primary trip if it reached a terminal state
//        if (result.requiresFareCalc()) {
//            fareCalculator.calculateAndApply(result.primaryTrip());
//        }
//
//        log.debug("Event processed: tapEventId={}, tripId={}, status={}",
//            tapEvent.getId(),
//            result.primaryTrip() != null ? result.primaryTrip().getId() : "none",
//            result.primaryTrip() != null ? result.primaryTrip().getStatus() : "none");
//    }
}
