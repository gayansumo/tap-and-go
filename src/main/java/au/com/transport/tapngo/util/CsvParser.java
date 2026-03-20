package au.com.transport.tapngo.util;

import au.com.transport.tapngo.domain.TapEvent;
import java.io.InputStream;
import java.util.stream.Stream;

/**
 * Streaming CSV parser interface.
 * Returns a lazy Stream so the caller controls when rows are consumed.
 * The stream MUST be closed after use (use try-with-resources).
 * <p>
 * Implementations must:
 * - Skip the header row
 * - Stream rows lazily (no full file load into memory)
 * - Close underlying resources when the stream is closed
 */
public interface CsvParser {

    /**
     * @param inputStream raw CSV bytes
     * @param sourceFile  filename for error reporting
     * @return lazy stream of ParsedRow (contains either a TapEvent or a failure)
     */
    Stream<ParsedRow> parse(InputStream inputStream, String sourceFile);

    /**
     * Represents the result of parsing one CSV row.
     * Either holds a valid TapEvent or a failure description.
     * This avoids exceptions in the stream pipeline.
     */
    record ParsedRow(
            TapEvent tapEvent,          // non-null if success
            String rawRow,              // original CSV line always preserved
            int rowNumber,
            String failureReason,       // non-null if failure
            boolean isValid
    ) {
        public static ParsedRow success(TapEvent tapEvent, String rawRow, int rowNumber) {
            return new ParsedRow(tapEvent, rawRow, rowNumber, null, true);
        }

        public static ParsedRow failure(String rawRow, int rowNumber, String reason) {
            return new ParsedRow(null, rawRow, rowNumber, reason, false);
        }
    }
}
