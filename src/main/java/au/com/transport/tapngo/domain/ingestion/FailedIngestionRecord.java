package au.com.transport.tapngo.domain.ingestion;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persists rows that failed CSV validation or DB/queue publishing.
 * These are NOT retried automatically — they require support intervention.
 * Kept separate from failed_events which are queue processing failures.
 */
@Entity
@Table(name = "failed_ingestion_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedIngestionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "failed_ingestion_seq")
    @SequenceGenerator(name = "failed_ingestion_seq", sequenceName = "failed_ingestion_seq", allocationSize = 50)
    private Long id;

    @Column(name = "raw_row", nullable = false, columnDefinition = "TEXT")
    private String rawRow;              // Original CSV line for replay

    @Column(name = "row_number")
    private Integer rowNumber;          // Line number in file for easy debugging

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", nullable = false, length = 30)
    private FailureType failureType;

    @Column(name = "source_file")
    private String sourceFile;          // Which file this came from

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum FailureType {
        VALIDATION_ERROR,       // Bad data in the row (missing field, wrong format)
        PUBLISH_FAILURE,        // Valid row but queue publish failed
        SYSTEM_ERROR            // Unexpected error
    }
}
