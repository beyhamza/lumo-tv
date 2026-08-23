package tv.lumo.api.source;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tv.lumo.api.auth.CurrentUser;
import tv.lumo.api.generated.api.SourcesApi;
import tv.lumo.api.generated.model.CreateSourceRequest;
import tv.lumo.api.generated.model.Source;
import tv.lumo.api.generated.model.SourceList;
import tv.lumo.api.generated.model.UpdateSourceRequest;

/**
 * Implements the generated {@code SourcesApi}.
 *
 * <p>Every operation resolves the caller from the security context and passes
 * that id down. A source id from the path is never trusted on its own: the
 * repository requires both, so a request for someone else's source is a 404
 * rather than a leak.
 */
@RestController
public class SourceController implements SourcesApi {

    private final SourceService sources;

    public SourceController(SourceService sources) {
        this.sources = sources;
    }

    @Override
    public ResponseEntity<SourceList> listSources() {
        SourceList list = new SourceList(sources.listOwned(CurrentUser.requireUserId()));
        return ResponseEntity.ok(list);
    }

    @Override
    public ResponseEntity<Source> createSource(CreateSourceRequest createSourceRequest) {
        Source created = sources.create(CurrentUser.requireUserId(), createSourceRequest);
        // 202, not 201: validation passed but the catalogue is not there yet.
        // The client polls GET /sources/{id} until READY or ERROR.
        return ResponseEntity.accepted()
                .location(URI.create("/v1/sources/" + created.getId()))
                .body(created);
    }

    @Override
    public ResponseEntity<Source> getSource(UUID id) {
        return ResponseEntity.ok(sources.getOwned(id, CurrentUser.requireUserId()));
    }

    @Override
    public ResponseEntity<Source> updateSource(UUID id, UpdateSourceRequest updateSourceRequest) {
        return ResponseEntity.ok(sources.update(id, CurrentUser.requireUserId(), updateSourceRequest));
    }

    @Override
    public ResponseEntity<Void> deleteSource(UUID id) {
        sources.delete(id, CurrentUser.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Source> syncSource(UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(sources.sync(id, CurrentUser.requireUserId()));
    }
}
