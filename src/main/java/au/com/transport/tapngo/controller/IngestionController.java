package au.com.transport.tapngo.controller;

import au.com.transport.tapngo.domain.ingestion.IngestionSummary;
import au.com.transport.tapngo.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST endpoint for CSV ingestion.
 * Accepts single or multiple files per request.
 * Returns a summary per file so callers know exactly what succeeded and failed.
 *
 * Authentication: X-API-Token header (machine-to-machine).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    /**
     * POST /api/v1/ingest
     * Content-Type: multipart/form-data
     *
     * Accepts one or more CSV files. Each file is processed independently.
     * A failure in one file does not affect others.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResponse> ingest(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(IngestionResponse.error("No files provided"));
        }

        log.info("Received ingestion request for {} file(s)", files.size());

        List<IngestionSummary> summaries = files.stream()
                .map(this::processFile)
                .toList();

        IngestionResponse response = IngestionResponse.from(summaries);

        // Return 207 Multi-Status if any file had failures but others succeeded
        // Return 200 if all succeeded, 422 if all failed
        int status = determineHttpStatus(summaries);
        return ResponseEntity.status(status).body(response);
    }

    private IngestionSummary processFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        log.info("Processing file: {}", filename);
        try {
            return ingestionService.ingest(file.getInputStream(), filename);
        } catch (IOException e) {
            log.error("Could not read file: {}", filename, e);
            return IngestionSummary.builder()
                    .sourceFile(filename)
                    .totalRows(0)
                    .successfulRows(0)
                    .failedRows(0)
                    .failures(List.of(new IngestionSummary.RowFailure(
                        0, "", "File could not be read: " + e.getMessage())))
                    .build();
        }
    }

    private int determineHttpStatus(List<IngestionSummary> summaries) {
        boolean anySuccess = summaries.stream().anyMatch(s -> s.getSuccessfulRows() > 0);
        boolean anyFailure = summaries.stream().anyMatch(IngestionSummary::hasFailures);

        if (anySuccess && anyFailure) return 207;   // Partial success
        if (!anySuccess) return 422;                // All failed
        return 200;
    }

    public record IngestionResponse(
        int totalFiles,
        int totalRowsProcessed,
        int totalRowsSucceeded,
        int totalRowsFailed,
        List<IngestionSummary> fileSummaries,
        String error
    ) {
        public static IngestionResponse from(List<IngestionSummary> summaries) {
            return new IngestionResponse(
                summaries.size(),
                summaries.stream().mapToInt(IngestionSummary::getTotalRows).sum(),
                summaries.stream().mapToInt(IngestionSummary::getSuccessfulRows).sum(),
                summaries.stream().mapToInt(IngestionSummary::getFailedRows).sum(),
                summaries,
                null
            );
        }

        public static IngestionResponse error(String message) {
            return new IngestionResponse(0, 0, 0, 0, List.of(), message);
        }
    }
}
