package tv.lumo.api.legal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Access to {@code legal_document_version} (ADR 0015).
 *
 * <p>Every write that changes content carries {@code status = 'DRAFT'} in its
 * {@code WHERE}: a published text cannot be altered by a request that read it as
 * a draft a moment earlier.
 */
@Repository
public class LegalVersionRepository {

    private static final String COLUMNS = """
            id, document_type, status, version, title_fr, title_en, body_fr, body_en,
            change_summary, published_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public LegalVersionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Within a document: the draft first, then the most recent version. */
    public List<LegalVersionRow> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM legal_document_version
                 ORDER BY document_type, version DESC NULLS FIRST
                """)
                .query(LegalVersionRepository::map)
                .list();
    }

    public Optional<LegalVersionRow> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM legal_document_version WHERE id = :id")
                .param("id", id)
                .query(LegalVersionRepository::map)
                .optional();
    }

    /** Holds the row until commit, so that two publications of one draft run one after the other. */
    public Optional<LegalVersionRow> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM legal_document_version WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(LegalVersionRepository::map)
                .optional();
    }

    public Optional<LegalVersionRow> findPublished(String documentType) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM legal_document_version
                 WHERE document_type = :type AND status = 'PUBLISHED'
                """)
                .param("type", documentType)
                .query(LegalVersionRepository::map)
                .optional();
    }

    /** @throws org.springframework.dao.DuplicateKeyException when the document already has a draft */
    public void insertDraft(UUID id, String documentType, LegalContent content) {
        jdbc.sql("""
                INSERT INTO legal_document_version
                    (id, document_type, status, title_fr, title_en, body_fr, body_en, change_summary)
                VALUES (:id, :type, 'DRAFT', :titleFr, :titleEn, :bodyFr, :bodyEn, :changeSummary)
                """)
                .param("id", id)
                .param("type", documentType)
                .param("titleFr", content.titleFr())
                .param("titleEn", content.titleEn())
                .param("bodyFr", content.bodyFr())
                .param("bodyEn", content.bodyEn())
                .param("changeSummary", content.changeSummary())
                .update();
    }

    /** @return the number of rows changed: 0 when {@code id} is not a draft */
    public int updateDraft(UUID id, LegalContent content) {
        return jdbc.sql("""
                UPDATE legal_document_version
                   SET title_fr = :titleFr, title_en = :titleEn,
                       body_fr = :bodyFr, body_en = :bodyEn,
                       change_summary = :changeSummary, updated_at = now()
                 WHERE id = :id AND status = 'DRAFT'
                """)
                .param("id", id)
                .param("titleFr", content.titleFr())
                .param("titleEn", content.titleEn())
                .param("bodyFr", content.bodyFr())
                .param("bodyEn", content.bodyEn())
                .param("changeSummary", content.changeSummary())
                .update();
    }

    /** @return the number of rows deleted: 0 when {@code id} is not a draft */
    public int deleteDraft(UUID id) {
        return jdbc.sql("DELETE FROM legal_document_version WHERE id = :id AND status = 'DRAFT'")
                .param("id", id)
                .update();
    }

    public void archivePublished(String documentType) {
        jdbc.sql("""
                UPDATE legal_document_version
                   SET status = 'ARCHIVED', updated_at = now()
                 WHERE document_type = :type AND status = 'PUBLISHED'
                """)
                .param("type", documentType)
                .update();
    }

    public int nextVersionNumber(String documentType) {
        return jdbc.sql("""
                SELECT COALESCE(MAX(version), 0) + 1
                  FROM legal_document_version
                 WHERE document_type = :type
                """)
                .param("type", documentType)
                .query(Integer.class)
                .single();
    }

    public void publish(UUID id, int version) {
        jdbc.sql("""
                UPDATE legal_document_version
                   SET status = 'PUBLISHED', version = :version,
                       published_at = now(), updated_at = now()
                 WHERE id = :id AND status = 'DRAFT'
                """)
                .param("id", id)
                .param("version", version)
                .update();
    }

    private static LegalVersionRow map(ResultSet rs, int rowNum) throws SQLException {
        return new LegalVersionRow(
                rs.getObject("id", UUID.class),
                rs.getString("document_type"),
                rs.getString("status"),
                rs.getObject("version", Integer.class),
                new LegalContent(
                        rs.getString("title_fr"),
                        rs.getString("title_en"),
                        rs.getString("body_fr"),
                        rs.getString("body_en"),
                        rs.getString("change_summary")),
                rs.getObject("published_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    /** A row of {@code legal_document_version}. */
    public record LegalVersionRow(
            UUID id,
            String documentType,
            String status,
            Integer version,
            LegalContent content,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    /** What an administrator writes in a version. */
    public record LegalContent(
            String titleFr,
            String titleEn,
            String bodyFr,
            String bodyEn,
            String changeSummary
    ) {

        /** FR and EN go out together (AGENTS.md §4). */
        boolean isComplete() {
            return !titleFr.isBlank() && !titleEn.isBlank() && !bodyFr.isBlank() && !bodyEn.isBlank();
        }
    }
}
