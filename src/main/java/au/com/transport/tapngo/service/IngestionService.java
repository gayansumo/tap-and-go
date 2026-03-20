package au.com.transport.tapngo.service;

import au.com.transport.tapngo.domain.TapEvent;
import au.com.transport.tapngo.domain.ingestion.FailedIngestionRecord;
import au.com.transport.tapngo.domain.ingestion.IngestionSummary;
import au.com.transport.tapngo.exception.TapProcessingException;
import au.com.transport.tapngo.messaging.TapEventPublisher;
import au.com.transport.tapngo.repository.FailedIngestionRepository;
import au.com.transport.tapngo.repository.TapEventRepository;
import au.com.transport.tapngo.util.CsvParser;
import au.com.transport.tapngo.util.ingestion.IngestionBatchBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Orchestrates the full CSV ingestion pipeline:
 * <p>
 * 1. Stream CSV rows lazily (flat memory regardless of file size)
 * 2. Buffer rows into batches
 * 3. On each batch flush:
 * a. @Transactional: save valid TapEvents to DB + save failed rows to DB
 * b. Publish valid TapEvent IDs to queue
 * c. If publish fails: mark those rows as FAILED in DB (not silently lost)
 * 4. Return IngestionSummary with full breakdown
 * <p>
 * Transaction boundary:
 * Each batch flush is its own transaction. A failure in one batch
 * rolls back only that batch — all other batches are committed.
 * This is intentional: partial success is correct for large file ingestion.
 * <p>
 * The queue publish is NOT inside the @Transactional boundary because
 * JMS and JPA cannot share a transaction without XA. Instead:
 * - DB write is committed first
 * - Queue publish happens after commit
 * - If publish fails, rows are marked FAILED in a separate transaction
 * - The OutboxDispatcher (scheduled job) will retry FAILED rows
 * This achieves at-least-once delivery without XA complexity.
 * See ADR-001 for full rationale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final CsvParser csvParser;
    private final TapEventRepository tapEventRepository;
    private final FailedIngestionRepository failedIngestionRepository;
    private final TapEventPublisher tapEventPublisher;

    @Value("${app.ingestion.batch-size}")
    private int batchSize;

    public IngestionSummary ingest(InputStream inputStream, String sourceFile) {
        log.info("Starting ingestion of file: {}", sourceFile);

        IngestionBatchBuffer buffer = new IngestionBatchBuffer(
                batchSize,
                (validBatch, failedBatch) -> flushBatch(validBatch, failedBatch, sourceFile)
        );

        // Stream rows — memory stays flat regardless of file size
        try (var rowStream = csvParser.parse(inputStream, sourceFile)) {
            rowStream.forEach(parsedRow -> buffer.add(parsedRow, sourceFile));
        }

        IngestionSummary summary = buffer.flushAndSummarise(sourceFile);

        log.info("Ingestion complete for {}: total={}, success={}, failed={}",
                sourceFile, summary.getTotalRows(),
                summary.getSuccessfulRows(), summary.getFailedRows());

        return summary;
    }

    /**
     * Saves a batch to DB then publishes to queue.
     * DB save and failed row save are in one transaction.
     * Queue publish is outside the transaction (see class javadoc).
     */
    private void flushBatch(List<TapEvent> validBatch,
                            List<FailedIngestionRecord> failedBatch,
                            String sourceFile) {
        List<TapEvent> savedEvents = saveBatchToDb(validBatch, failedBatch);

        if (!savedEvents.isEmpty()) {
            publishBatch(savedEvents, sourceFile);
        }
    }

    /**
     * Single transaction: save valid events + save failed records atomically.
     * If this fails, the entire batch is rolled back — no partial saves.
     */
    @Transactional
    protected List<TapEvent> saveBatchToDb(List<TapEvent> validBatch,
                                           List<FailedIngestionRecord> failedBatch) {
        List<TapEvent> saved = List.of();

        if (!validBatch.isEmpty()) {
            saved = tapEventRepository.saveAll(validBatch);
            log.debug("Saved {} tap events to DB", saved.size());
        }

        if (!failedBatch.isEmpty()) {
            failedIngestionRepository.saveAll(failedBatch);
            log.debug("Saved {} failed ingestion records to DB", failedBatch.size());
        }

        return saved;
    }

    /**
     * Publishes saved events to queue.
     * Outside @Transactional — see class javadoc for rationale.
     * On failure: marks events as FAILED so OutboxDispatcher can retry.
     */
    private void publishBatch(List<TapEvent> savedEvents, String sourceFile) {
        try {
            tapEventPublisher.publishBatch(savedEvents);
            markPublished(savedEvents);
        } catch (TapProcessingException e) {
            log.error("Publish failed for batch of {} events from {}, marking as FAILED",
                    savedEvents.size(), sourceFile, e);
            markFailed(savedEvents);
        }
    }

    @Transactional
    protected void markPublished(List<TapEvent> events) {
        List<Long> ids = events.stream().map(TapEvent::getId).toList();
        tapEventRepository.markAsPublished(ids);
    }

    @Transactional
    protected void markFailed(List<TapEvent> events) {
        List<Long> ids = events.stream().map(TapEvent::getId).toList();
        tapEventRepository.markAsFailed(ids);
    }
}
