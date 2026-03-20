package au.com.transport.tapngo.repository;

import au.com.transport.tapngo.domain.ingestion.FailedIngestionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedIngestionRepository extends JpaRepository<FailedIngestionRecord, Long> {

    List<FailedIngestionRecord> findBySourceFile(String sourceFile);
}
