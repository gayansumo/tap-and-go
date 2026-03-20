package au.com.transport.tapngo.util.ingestion;

import au.com.transport.tapngo.domain.TapEvent;
import au.com.transport.tapngo.domain.ingestion.FailedIngestionRecord;
import au.com.transport.tapngo.domain.ingestion.IngestionSummary;
import au.com.transport.tapngo.util.CsvParser;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates parsed CSV rows and triggers a flush when the batch size
 * threshold is reached. This is NOT a Spring bean — it is created per
 * ingestion request by IngestionService so it carries no shared state.
 *
 * Design note:
 * The buffer separates valid rows and failed rows from the start.
 * Valid rows accumulate for batch DB insert + queue publish.
 * Failed rows accumulate for batch DB insert into failed_ingestion.
 * Both are flushed together when either threshold is hit.
 */
@Slf4j
public class IngestionBatchBuffer {

    private final int batchSize;
    private final FlushCallback flushCallback;

    private final List<TapEvent> validBatch = new ArrayList<>();
    private final List<FailedIngestionRecord> failedBatch = new ArrayList<>();

    private int totalProcessed = 0;
    private int totalSuccess = 0;
    private int totalFailed = 0;
    private final List<IngestionSummary.RowFailure> allFailures = new ArrayList<>();

    public IngestionBatchBuffer(int batchSize, FlushCallback flushCallback) {
        this.batchSize = batchSize;
        this.flushCallback = flushCallback;
    }

    public void add(CsvParser.ParsedRow parsedRow, String sourceFile) {
        totalProcessed++;

        if (parsedRow.isValid()) {
            validBatch.add(parsedRow.tapEvent());
            totalSuccess++;
        } else {
            failedBatch.add(buildFailedRecord(parsedRow, sourceFile));
            allFailures.add(new IngestionSummary.RowFailure(
                parsedRow.rowNumber(),
                parsedRow.rawRow(),
                parsedRow.failureReason()
            ));
            totalFailed++;
        }

        if (validBatch.size() >= batchSize || failedBatch.size() >= batchSize) {
            flush();
        }
    }

    /**
     * Must be called after stream ends to flush any remaining rows
     * that didn't reach the batch size threshold.
     */
    public IngestionSummary flushAndSummarise(String sourceFile) {
        if (!validBatch.isEmpty() || !failedBatch.isEmpty()) {
            flush();
        }

        return IngestionSummary.builder()
                .sourceFile(sourceFile)
                .totalRows(totalProcessed)
                .successfulRows(totalSuccess)
                .failedRows(totalFailed)
                .failures(List.copyOf(allFailures))
                .build();
    }

    private void flush() {
        List<TapEvent> validToFlush = List.copyOf(validBatch);
        List<FailedIngestionRecord> failedToFlush = List.copyOf(failedBatch);

        validBatch.clear();
        failedBatch.clear();

        log.debug("Flushing batch: {} valid, {} failed", validToFlush.size(), failedToFlush.size());
        flushCallback.flush(validToFlush, failedToFlush);
    }

    private FailedIngestionRecord buildFailedRecord(CsvParser.ParsedRow row, String sourceFile) {
        return FailedIngestionRecord.builder()
                .rawRow(row.rawRow())
                .rowNumber(row.rowNumber())
                .failureReason(row.failureReason())
                .failureType(FailedIngestionRecord.FailureType.VALIDATION_ERROR)
                .sourceFile(sourceFile)
                .build();
    }

    /**
     * Callback interface so IngestionService controls the transactional flush.
     * The buffer has no Spring dependency — clean separation.
     */
    @FunctionalInterface
    public interface FlushCallback {
        void flush(List<TapEvent> valid, List<FailedIngestionRecord> failed);
    }
}
