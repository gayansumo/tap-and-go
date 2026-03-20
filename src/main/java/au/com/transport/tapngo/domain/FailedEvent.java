package au.com.transport.tapngo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persists events that could not be processed after all retries.
 * Distinct from FailedIngestionRecord which is for CSV parsing failures.
 *
 * FailedIngestionRecord = producer side (bad CSV row)
 * FailedEvent           = consumer side (valid event, processing failed)
 */
@Entity
@Table(name = "failed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "failed_event_seq")
    @SequenceGenerator(name = "failed_event_seq", sequenceName = "failed_event_seq", allocationSize = 50)
    private Long id;

    @Column(name = "tap_event_id")
    private Long tapEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 30)
    private FailureType failureType;

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;         // JSON snapshot for support replay

    @Column(name = "retry_count")
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FailureType {
        UNMATCHED_TAP_OFF,          // TAP OFF with no preceding TAP ON
        MAX_RETRIES_EXCEEDED,       // Processing failed after all JMS retries
        DUPLICATE_EVENT,            // Already processed — logged but not stored usually
        SYSTEM_ERROR                // Unexpected failure
    }
}
