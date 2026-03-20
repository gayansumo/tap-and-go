package au.com.transport.tapngo.util.ingestion;

import au.com.transport.tapngo.domain.TapEvent;
import au.com.transport.tapngo.exception.InvalidTapDataException;
import au.com.transport.tapngo.util.CsvParser;
import au.com.transport.tapngo.util.CsvRowMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * OpenCSV-backed streaming CSV parser.
 *
 * Key design decisions:
 * - Streams row-by-row via iterator, never loads full file into memory
 * - Row parsing failures are captured as ParsedRow.failure(), not thrown
 *   so a single bad row never terminates the stream
 * - The CSVReader is closed when the returned stream is closed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TapEventCsvParser implements CsvParser {

    private final CsvRowMapper rowMapper;

    @Override
    public Stream<ParsedRow> parse(InputStream inputStream, String sourceFile) {
        RFC4180Parser csvParser = new RFC4180ParserBuilder().build();
        CSVReader csvReader = new CSVReaderBuilder(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(csvParser)
                .withSkipLines(1)           // Skip header row
                .build();

        var iterator = new RowIterator(csvReader, rowMapper, sourceFile);

        Spliterator<ParsedRow> spliterator = Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED | Spliterator.NONNULL
        );

        // onClose ensures CSVReader is closed when stream.close() is called
        return StreamSupport.stream(spliterator, false)
                .onClose(() -> closeQuietly(csvReader, sourceFile));
    }

    private void closeQuietly(CSVReader reader, String sourceFile) {
        try {
            reader.close();
        } catch (IOException e) {
            log.warn("Failed to close CSVReader for file: {}", sourceFile, e);
        }
    }

    /**
     * Iterator that wraps CSVReader and converts each row to ParsedRow.
     * Catches row-level exceptions so one bad row does not stop the stream.
     */
    private static class RowIterator implements java.util.Iterator<ParsedRow> {

        private final CSVReader csvReader;
        private final CsvRowMapper rowMapper;
        private final String sourceFile;
        private String[] nextRow;
        private int rowNumber = 1;          // 1-based, header is row 0

        RowIterator(CSVReader csvReader, CsvRowMapper rowMapper, String sourceFile) {
            this.csvReader = csvReader;
            this.rowMapper = rowMapper;
            this.sourceFile = sourceFile;
            advance();
        }

        @Override
        public boolean hasNext() {
            return nextRow != null;
        }

        @Override
        public ParsedRow next() {
            String[] current = nextRow;
            int currentRowNumber = rowNumber++;
            advance();
            return toParseResult(current, currentRowNumber);
        }

        private void advance() {
            try {
                nextRow = csvReader.readNext();
            } catch (Exception e) {
                log.error("Failed to read next row from {}", sourceFile, e);
                nextRow = null;
            }
        }

        private ParsedRow toParseResult(String[] columns, int rowNum) {
            String rawRow = columns == null ? "" : String.join(",", Arrays.asList(columns));
            try {
                TapEvent tapEvent = rowMapper.map(columns, rowNum);
                return ParsedRow.success(tapEvent, rawRow, rowNum);
            } catch (InvalidTapDataException e) {
                log.warn("Invalid row {} in {}: {}", rowNum, sourceFile, e.getMessage());
                return ParsedRow.failure(rawRow, rowNum, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error parsing row {} in {}", rowNum, sourceFile, e);
                return ParsedRow.failure(rawRow, rowNum, "Unexpected error: " + e.getMessage());
            }
        }
    }
}
