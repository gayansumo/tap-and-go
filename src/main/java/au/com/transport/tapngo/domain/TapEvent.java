package au.com.transport.tapngo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a single tap event captured at a transit terminal.
 * This is the raw event as received — no business logic here.
 */
@Entity
@Table(name = "tap_events", indexes = {
    @Index(name = "idx_tap_events_pan_status", columnList = "pan, status"),
    @Index(name = "idx_tap_events_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tap_event_seq")
    @SequenceGenerator(name = "tap_event_seq", sequenceName = "tap_event_seq", allocationSize = 100)
    private Long id;

    @Column(name = "tap_id", nullable = false)
    private Long tapId;                     // Original ID from CSV

    @Column(name = "tapped_at", nullable = false)
    private LocalDateTime tappedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tap_type", nullable = false, length = 3)
    private TapType tapType;

    @Column(name = "stop_id", nullable = false, length = 10)
    private String stopId;

    @Column(name = "company_id", nullable = false, length = 50)
    private String companyId;

    @Column(name = "bus_id", nullable = false, length = 50)
    private String busId;

    /**
     * Primary Account Number — the card identifier.
     * Stored as-is; masking happens at the API response layer.
     */
    @Column(name = "pan", nullable = false, length = 50)
    private String pan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TapEventStatus status = TapEventStatus.RECEIVED;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TapEventStatus {
        RECEIVED,       // Saved from CSV, not yet processed
        PUBLISHED,      // Successfully pushed to queue
        FAILED          // Could not publish after retries
    }
}
