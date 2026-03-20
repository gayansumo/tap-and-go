package au.com.transport.tapngo.util;

import au.com.transport.tapngo.domain.TapEvent;
import au.com.transport.tapngo.domain.TapType;
import au.com.transport.tapngo.exception.InvalidTapDataException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Maps a raw CSV string array to a TapEvent domain object.
 * Validates each field and throws InvalidTapDataException (non-retryable)
 * for any data problem. This keeps validation logic out of the parser.
 *
 * Expected CSV format:
 * ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
 */
@Component
public class CsvRowMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final int EXPECTED_COLUMNS = 7;
    private static final int IDX_ID       = 0;
    private static final int IDX_DATETIME = 1;
    private static final int IDX_TAPTYPE  = 2;
    private static final int IDX_STOP     = 3;
    private static final int IDX_COMPANY  = 4;
    private static final int IDX_BUS      = 5;
    private static final int IDX_PAN      = 6;

    public TapEvent map(String[] columns, int rowNumber) {
        validateColumnCount(columns, rowNumber);

        String rawId       = clean(columns[IDX_ID]);
        String rawDatetime = clean(columns[IDX_DATETIME]);
        String rawTapType  = clean(columns[IDX_TAPTYPE]);
        String rawStop     = clean(columns[IDX_STOP]);
        String rawCompany  = clean(columns[IDX_COMPANY]);
        String rawBus      = clean(columns[IDX_BUS]);
        String rawPan      = clean(columns[IDX_PAN]);

        return TapEvent.builder()
                .tapId(parseLong(rawId, "ID", rowNumber))
                .tappedAt(parseDateTime(rawDatetime, rowNumber))
                .tapType(parseTapType(rawTapType, rowNumber))
                .stopId(requireNonEmpty(rawStop, "StopId", rowNumber))
                .companyId(requireNonEmpty(rawCompany, "CompanyId", rowNumber))
                .busId(requireNonEmpty(rawBus, "BusID", rowNumber))
                .pan(requireNonEmpty(rawPan, "PAN", rowNumber))
                .build();
    }

    private void validateColumnCount(String[] columns, int rowNumber) {
        if (columns == null || columns.length < EXPECTED_COLUMNS) {
            throw new InvalidTapDataException(
                "Row %d: expected %d columns but found %d"
                    .formatted(rowNumber, EXPECTED_COLUMNS, columns == null ? 0 : columns.length)
            );
        }
    }

    private Long parseLong(String value, String field, int rowNumber) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InvalidTapDataException(
                "Row %d: field '%s' is not a valid number: '%s'".formatted(rowNumber, field, value)
            );
        }
    }

    private LocalDateTime parseDateTime(String value, int rowNumber) {
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidTapDataException(
                "Row %d: datetime '%s' does not match expected format 'dd-MM-yyyy HH:mm:ss'"
                    .formatted(rowNumber, value)
            );
        }
    }

    private TapType parseTapType(String value, int rowNumber) {
        try {
            return TapType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidTapDataException(
                "Row %d: tap type '%s' is invalid, expected ON or OFF".formatted(rowNumber, value)
            );
        }
    }

    private String requireNonEmpty(String value, String field, int rowNumber) {
        if (value == null || value.isBlank()) {
            throw new InvalidTapDataException(
                "Row %d: field '%s' is required but was empty".formatted(rowNumber, field)
            );
        }
        return value;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
