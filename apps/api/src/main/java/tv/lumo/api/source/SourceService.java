package tv.lumo.api.source;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tv.lumo.api.generated.model.CreateSourceRequest;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;
import tv.lumo.api.generated.model.Source;
import tv.lumo.api.generated.model.SourceKind;
import tv.lumo.api.generated.model.SourceStatus;
import tv.lumo.api.generated.model.UpdateSourceRequest;
import tv.lumo.api.ingest.HostNormaliser;
import tv.lumo.api.ingest.IngestionException;
import tv.lumo.api.ingest.IngestionService;
import tv.lumo.api.ingest.SourceUrl;
import tv.lumo.api.ingest.xtream.XtreamClient;
import tv.lumo.api.shared.crypto.CredentialCipher;
import tv.lumo.api.shared.error.ApiException;

/**
 * Registering, updating and re-synchronising sources.
 *
 * <p>The two-surface error model the contract describes lives here:
 *
 * <ol>
 *   <li><b>Synchronous validation.</b> Reachability and credentials are checked
 *       before answering. A failure is a 422 carrying an {@code IngestionErrorCode}
 *       and <b>no source is created</b> — which is what puts "your credentials
 *       were refused by the server" in front of the user while they are still
 *       looking at the form (US-06).</li>
 *   <li><b>Asynchronous ingestion.</b> Once validation passes the source is saved
 *       {@code PENDING} and catalogue parsing runs in the background. A failure
 *       there is not an HTTP error: the row moves to {@code ERROR} with an
 *       {@code error_code} the polling client reads.</li>
 * </ol>
 *
 * <p>Every read and write goes through a repository method that requires the
 * caller's {@code userId} (docs/architecture.md §2).
 */
@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sources;
    private final IngestionService ingestion;
    private final XtreamClient xtream;
    private final CredentialCipher cipher;
    private final SourceMapper mapper;

    public SourceService(SourceRepository sources,
                         IngestionService ingestion,
                         XtreamClient xtream,
                         CredentialCipher cipher,
                         SourceMapper mapper) {
        this.sources = sources;
        this.ingestion = ingestion;
        this.xtream = xtream;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    public List<Source> listOwned(UUID userId) {
        return sources.findAllOwnedBy(userId).stream()
                .map(row -> mapper.toApi(row, channelCount(row)))
                .toList();
    }

    public Source getOwned(UUID sourceId, UUID userId) {
        SourceRepository.SourceRow row = requireOwned(sourceId, userId);
        return mapper.toApi(row, channelCount(row));
    }

    /**
     * Validates, persists and schedules ingestion.
     *
     * <p>Not {@code @Transactional} as a whole on purpose: the validation call
     * reaches out to a third-party server and can take seconds, and holding a
     * database connection across that would tie pool capacity to how slow a
     * stranger's panel is (ADR 0005 §2).
     */
    public Source create(UUID userId, CreateSourceRequest request) {
        requireConsistentShape(request);

        UUID sourceId = UUID.randomUUID();
        // Normalised once. US-06 requires accepting a host with or without a
        // scheme, port or trailing slash, and what gets stored is the normal form.
        String host = request.getKind() == SourceKind.XTREAM
                ? HostNormaliser.normalise(request.getHost())
                : null;

        byte[] sealedPassword = null;
        java.time.OffsetDateTime expiresAt = null;
        Integer maxConnections = null;

        if (request.getKind() == SourceKind.XTREAM) {
            // Synchronous: this call is what decides between 422 and 202.
            XtreamClient.XtreamAccount account = validate(() ->
                    xtream.authenticate(host, request.getUsername(), request.getPassword()));
            expiresAt = account.expiresAt();
            maxConnections = account.maxConnections();
            sealedPassword = cipher.seal(request.getPassword());
        }

        // Both kinds may carry an EPG URL, so this runs for both. Rejecting now
        // rather than at ingestion means the client hears 422 instead of being
        // told PENDING and then polling its way to an error.
        requireFetchableUrls(request.getM3uUrl(), request.getEpgUrl());

        // One statement, so atomic on its own; no transaction is opened around
        // the third-party call above (ADR 0005 §2).
        sources.insert(sourceId, userId, request.getLabel(), request.getKind(), host,
                request.getUsername(), sealedPassword, request.getM3uUrl(), request.getEpgUrl(),
                expiresAt, maxConnections);

        ingestion.schedule(sourceId);

        // channelCount stays null: ingestion has only just been scheduled.
        return mapper.toApi(requireOwned(sourceId, userId), null);
    }

    public Source update(UUID sourceId, UUID userId, UpdateSourceRequest request) {
        requireOwned(sourceId, userId);

        // Same rule as on create. Without it a PATCH is a way around the
        // validation: the row would take any string and the failure would only
        // surface inside the ingestion worker.
        requireFetchableUrls(request.getM3uUrl(), request.getEpgUrl());

        byte[] sealedPassword = request.getPassword() == null ? null : cipher.seal(request.getPassword());
        String host = request.getHost() == null ? null : HostNormaliser.normalise(request.getHost());

        // Anything that changes what gets fetched sends the source back through
        // ingestion; a label change does not.
        boolean reingest = host != null
                || request.getUsername() != null
                || request.getPassword() != null
                || request.getM3uUrl() != null
                || request.getEpgUrl() != null;

        sources.update(sourceId, userId, request.getLabel(), host, request.getUsername(),
                sealedPassword, request.getM3uUrl(), request.getEpgUrl(), reingest);

        if (reingest) {
            ingestion.schedule(sourceId);
        }
        SourceRepository.SourceRow row = requireOwned(sourceId, userId);
        return mapper.toApi(row, channelCount(row));
    }

    @Transactional
    public void delete(UUID sourceId, UUID userId) {
        if (sources.delete(sourceId, userId) == 0) {
            throw ApiException.notFound(ErrorCode.SOURCE_NOT_FOUND, "No such source on this account");
        }
        // Categories, channels, EPG rows and favourites go with it, by ON DELETE
        // CASCADE rather than by application code that could miss one.
        log.info("Source {} deleted", sourceId);
    }

    public Source sync(UUID sourceId, UUID userId) {
        requireOwned(sourceId, userId);
        if (!ingestion.schedule(sourceId)) {
            throw ApiException.conflict(ErrorCode.SOURCE_SYNC_IN_PROGRESS,
                    "A synchronisation is already running for this source");
        }
        SourceRepository.SourceRow row = requireOwned(sourceId, userId);
        return mapper.toApi(row, channelCount(row));
    }

    // ---- helpers ------------------------------------------------------------

    private SourceRepository.SourceRow requireOwned(UUID sourceId, UUID userId) {
        return sources.findOwned(sourceId, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SOURCE_NOT_FOUND,
                        "No such source on this account"));
    }

    /**
     * Refuses any URL ingestion could not fetch, before it is stored.
     *
     * <p>Null means "not supplied" and is skipped — a PATCH sends only what it
     * changes, and an M3U source has no EPG URL until someone adds one.
     *
     * <p>The check is {@link SourceUrl#parse}, so it is exactly the check the
     * ingestion worker applies. That symmetry is the point: a URL that would be
     * refused later must not be accepted now.
     */
    private void requireFetchableUrls(String m3uUrl, String epgUrl) {
        if (m3uUrl != null) {
            validate(() -> SourceUrl.parse(m3uUrl));
        }
        if (epgUrl != null) {
            validate(() -> SourceUrl.parse(epgUrl));
        }
    }

    /** Null until the first successful ingestion, which is what the contract says. */
    private Integer channelCount(SourceRepository.SourceRow row) {
        return row.status() == SourceStatus.READY ? sources.countChannels(row.id()) : null;
    }

    /**
     * Turns an ingestion failure into the 422 the contract defines.
     *
     * <p>The code is preserved exactly. Collapsing SOURCE_UNREACHABLE and
     * SOURCE_AUTH_FAILED into one generic error is the single most damaging thing
     * this layer could do: the user's next action differs completely between "the
     * server is down" and "your password is wrong".
     */
    private <T> T validate(java.util.concurrent.Callable<T> validation) {
        try {
            return validation.call();
        } catch (IngestionException e) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.fromValue(e.code().getValue()), e.getMessage());
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable(ErrorCode.SOURCE_INVALID_FORMAT, "The URL is not valid");
        } catch (Exception e) {
            IngestionException mapped = IngestionException.from(e);
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.fromValue(mapped.code().getValue()), mapped.getMessage());
        }
    }

    /**
     * Checks that the properties present match the declared kind.
     *
     * <p>The contract documents this table but OpenAPI cannot express it, so it is
     * enforced here and reported per field.
     */
    private static void requireConsistentShape(CreateSourceRequest request) {
        List<FieldError> errors = new ArrayList<>();
        switch (request.getKind()) {
            case XTREAM -> {
                requirePresent(errors, "/host", request.getHost());
                requirePresent(errors, "/username", request.getUsername());
                requirePresent(errors, "/password", request.getPassword());
            }
            case M3U_URL -> requirePresent(errors, "/m3u_url", request.getM3uUrl());
            case M3U_FILE -> errors.add(new FieldError("/kind", "UNSUPPORTED"));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation("Request does not match the declared source kind", errors);
        }
    }

    private static void requirePresent(List<FieldError> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldError(field, "REQUIRED"));
        }
    }
}
