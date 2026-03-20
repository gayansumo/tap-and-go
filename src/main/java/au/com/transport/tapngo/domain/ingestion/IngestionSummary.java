package au.com.transport.tapngo.domain.ingestion;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Summary returned to the caller after ingesting a CSV file.
 * Gives full visibility into what succeeded, what failed, and why.
 */
@Getter
@Builder
public class IngestionSummary {

    private final String sourceFile;
    private final int totalRows;
    private final int successfulRows;
    private final int failedRows;
    private final List<RowFailure> failures;

    public record RowFailure(int rowNumber, String rawRow, String reason) {}

    public boolean hasFailures() {
        return failedRows > 0;
    }

    public boolean isCompleteSuccess() {
        return failedRows == 0 && successfulRows > 0;
    }
}
