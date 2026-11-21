package tv.lumo.api.legal;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.generated.api.LegalApi;
import tv.lumo.api.generated.model.CreateLegalDraftRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.LegalDocumentType;
import tv.lumo.api.generated.model.LegalDocumentVersion;
import tv.lumo.api.generated.model.LegalDocumentVersionList;
import tv.lumo.api.generated.model.LegalVersionStatus;
import tv.lumo.api.generated.model.UpdateLegalDraftRequest;
import tv.lumo.api.legal.LegalVersionRepository.LegalContent;
import tv.lumo.api.legal.LegalVersionRepository.LegalVersionRow;
import tv.lumo.api.shared.error.ApiException;

/**
 * Implements the generated {@code LegalApi}: versioned legal documents (ADR 0015).
 *
 * <p>{@code GET /legal/{document_type}} is public and only ever reads the version
 * in force. Everything else sits under {@code /admin}, which
 * {@code SecurityConfig} closes before any of these methods can run.
 */
@RestController
public class LegalController implements LegalApi {

    private static final Logger log = LoggerFactory.getLogger(LegalController.class);

    private final LegalVersionRepository versions;

    public LegalController(LegalVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public ResponseEntity<LegalDocumentVersion> getPublishedLegalDocument(LegalDocumentType documentType) {
        return versions.findPublished(documentType.getValue())
                .map(row -> ResponseEntity.ok(toApi(row)))
                .orElseThrow(() -> ApiException.notFound(ErrorCode.LEGAL_DOCUMENT_NOT_PUBLISHED,
                        "Nothing published for this document"));
    }

    @Override
    public ResponseEntity<LegalDocumentVersionList> listLegalVersions() {
        List<LegalDocumentVersion> items = versions.findAll().stream()
                .map(LegalController::toApi)
                .toList();
        return ResponseEntity.ok(new LegalDocumentVersionList(items));
    }

    @Override
    public ResponseEntity<LegalDocumentVersion> getLegalVersion(UUID id) {
        return ResponseEntity.ok(toApi(require(id)));
    }

    @Override
    public ResponseEntity<LegalDocumentVersion> createLegalDraft(CreateLegalDraftRequest request) {
        UUID id = UUID.randomUUID();
        try {
            versions.insertDraft(id, request.getDocumentType().getValue(), new LegalContent(
                    request.getTitleFr(), request.getTitleEn(),
                    request.getBodyFr(), request.getBodyEn(),
                    request.getChangeSummary()));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict(ErrorCode.LEGAL_DRAFT_ALREADY_EXISTS,
                    "This document already has a draft");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toApi(require(id)));
    }

    @Override
    public ResponseEntity<LegalDocumentVersion> updateLegalDraft(UUID id, UpdateLegalDraftRequest request) {
        int updated = versions.updateDraft(id, new LegalContent(
                request.getTitleFr(), request.getTitleEn(),
                request.getBodyFr(), request.getBodyEn(),
                request.getChangeSummary()));
        if (updated == 0) {
            throw notADraft(id);
        }
        return ResponseEntity.ok(toApi(require(id)));
    }

    @Override
    public ResponseEntity<Void> deleteLegalDraft(UUID id) {
        if (versions.deleteDraft(id) == 0) {
            throw notADraft(id);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * In one transaction, under a lock on the draft: the document never shows two
     * versions in force, nor none, and a second click finds a version that is no
     * longer a draft.
     */
    @Override
    @Transactional
    public ResponseEntity<LegalDocumentVersion> publishLegalDraft(UUID id) {
        LegalVersionRow draft = versions.findByIdForUpdate(id)
                .orElseThrow(() -> notFound());
        if (!LegalVersionStatus.DRAFT.getValue().equals(draft.status())) {
            throw notDraft();
        }
        if (!draft.content().isComplete()) {
            throw ApiException.unprocessable(ErrorCode.LEGAL_VERSION_INCOMPLETE,
                    "A title or a body is empty");
        }

        String documentType = draft.documentType();
        int number = versions.nextVersionNumber(documentType);
        versions.archivePublished(documentType);
        versions.publish(id, number);
        log.info("Legal document {} published as version {}", documentType, number);
        return ResponseEntity.ok(toApi(require(id)));
    }

    // ---- helpers ------------------------------------------------------------

    private LegalVersionRow require(UUID id) {
        return versions.findById(id).orElseThrow(LegalController::notFound);
    }

    /** A write that changed nothing: either the row is gone, or it left the draft state. */
    private ApiException notADraft(UUID id) {
        return versions.findById(id).isPresent() ? notDraft() : notFound();
    }

    private static ApiException notFound() {
        return ApiException.notFound(ErrorCode.LEGAL_VERSION_NOT_FOUND, "No such version");
    }

    private static ApiException notDraft() {
        return ApiException.conflict(ErrorCode.LEGAL_VERSION_NOT_DRAFT,
                "A published version cannot change");
    }

    private static LegalDocumentVersion toApi(LegalVersionRow row) {
        LegalContent content = row.content();
        LegalDocumentVersion version = new LegalDocumentVersion(
                row.id(),
                LegalDocumentType.fromValue(row.documentType()),
                LegalVersionStatus.fromValue(row.status()),
                content.titleFr(), content.titleEn(),
                content.bodyFr(), content.bodyEn(),
                row.createdAt(), row.updatedAt());
        version.setVersion(row.version());
        version.setChangeSummary(content.changeSummary());
        version.setPublishedAt(row.publishedAt());
        return version;
    }
}
