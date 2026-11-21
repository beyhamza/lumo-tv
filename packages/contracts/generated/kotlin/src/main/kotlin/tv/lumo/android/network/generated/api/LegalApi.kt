package tv.lumo.android.network.generated.api

import tv.lumo.android.network.generated.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import tv.lumo.android.network.generated.model.CreateLegalDraftRequest
import tv.lumo.android.network.generated.model.LegalDocumentType
import tv.lumo.android.network.generated.model.LegalDocumentVersion
import tv.lumo.android.network.generated.model.LegalDocumentVersionList
import tv.lumo.android.network.generated.model.Problem
import tv.lumo.android.network.generated.model.UpdateLegalDraftRequest

interface LegalApi {
    /**
     * POST admin/legal/versions
     * Start a new draft of a legal document
     * Administrators only. A document has **at most one draft**; a second one answers &#x60;409 LEGAL_DRAFT_ALREADY_EXISTS&#x60;. Texts may be empty while drafting. Publishing is what requires them. 
     * Responses:
     *  - 201: The draft.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *  - 409: This document already has a draft (`LEGAL_DRAFT_ALREADY_EXISTS`).
     *
     * @param createLegalDraftRequest 
     * @return [LegalDocumentVersion]
     */
    @POST("admin/legal/versions")
    suspend fun createLegalDraft(@Body createLegalDraftRequest: CreateLegalDraftRequest): Response<LegalDocumentVersion>

    /**
     * DELETE admin/legal/versions/{id}
     * Discard a draft
     * Administrators only. Published and archived versions cannot be deleted.
     * Responses:
     *  - 204: The draft is gone.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *  - 404: No such version (`LEGAL_VERSION_NOT_FOUND`).
     *  - 409: This version has been published and cannot change (`LEGAL_VERSION_NOT_DRAFT`).
     *
     * @param id Resource identifier.
     * @return [Unit]
     */
    @DELETE("admin/legal/versions/{id}")
    suspend fun deleteLegalDraft(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * GET admin/legal/versions/{id}
     * One version, whatever its status
     * Administrators only.
     * Responses:
     *  - 200: The version.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *  - 404: No such version (`LEGAL_VERSION_NOT_FOUND`).
     *
     * @param id Resource identifier.
     * @return [LegalDocumentVersion]
     */
    @GET("admin/legal/versions/{id}")
    suspend fun getLegalVersion(@Path("id") id: java.util.UUID): Response<LegalDocumentVersion>

    /**
     * GET legal/{document_type}
     * The version of a legal document currently in force
     * Public, and the only legal read a non-administrator has: drafts and archived versions are not reachable here. Both languages travel together, and the caller renders its own. 
     * Responses:
     *  - 200: The published version.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 404: Nothing published for this document yet (`LEGAL_DOCUMENT_NOT_PUBLISHED`).
     *
     * @param documentType 
     * @return [LegalDocumentVersion]
     */
    @GET("legal/{document_type}")
    suspend fun getPublishedLegalDocument(@Path("document_type") documentType: LegalDocumentType): Response<LegalDocumentVersion>

    /**
     * GET admin/legal/versions
     * Every version of every legal document
     * Administrators only. Newest first within each document.
     * Responses:
     *  - 200: The versions.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *
     * @return [LegalDocumentVersionList]
     */
    @GET("admin/legal/versions")
    suspend fun listLegalVersions(): Response<LegalDocumentVersionList>

    /**
     * POST admin/legal/versions/{id}/publish
     * Put a draft in force
     * Administrators only. Numbers the version, stamps &#x60;published_at&#x60; and moves the previous published version of the document to &#x60;ARCHIVED&#x60;, in one transaction. A title or a body missing in either language answers &#x60;422 LEGAL_VERSION_INCOMPLETE&#x60;: FR and EN go out together. 
     * Responses:
     *  - 200: The version now in force.
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *  - 404: No such version (`LEGAL_VERSION_NOT_FOUND`).
     *  - 409: This version has been published and cannot change (`LEGAL_VERSION_NOT_DRAFT`).
     *  - 422: A title or a body is empty (`LEGAL_VERSION_INCOMPLETE`).
     *
     * @param id Resource identifier.
     * @return [LegalDocumentVersion]
     */
    @POST("admin/legal/versions/{id}/publish")
    suspend fun publishLegalDraft(@Path("id") id: java.util.UUID): Response<LegalDocumentVersion>

    /**
     * PUT admin/legal/versions/{id}
     * Replace the content of a draft
     * Administrators only. Only a draft can change: a version that has been published is a record of what people were told and stays as it was (&#x60;409 LEGAL_VERSION_NOT_DRAFT&#x60;). 
     * Responses:
     *  - 200: The updated draft.
     *  - 400: The request is malformed or fails validation (`VALIDATION_FAILED`).
     *  - 401: Missing, malformed or expired access token (`UNAUTHENTICATED`, `ACCESS_TOKEN_EXPIRED`). On `ACCESS_TOKEN_EXPIRED` the client refreshes once and replays the request. 
     *  - 403: The caller is signed in and is not an administrator (`FORBIDDEN`).
     *  - 404: No such version (`LEGAL_VERSION_NOT_FOUND`).
     *  - 409: This version has been published and cannot change (`LEGAL_VERSION_NOT_DRAFT`).
     *
     * @param id Resource identifier.
     * @param updateLegalDraftRequest 
     * @return [LegalDocumentVersion]
     */
    @PUT("admin/legal/versions/{id}")
    suspend fun updateLegalDraft(@Path("id") id: java.util.UUID, @Body updateLegalDraftRequest: UpdateLegalDraftRequest): Response<LegalDocumentVersion>

}
