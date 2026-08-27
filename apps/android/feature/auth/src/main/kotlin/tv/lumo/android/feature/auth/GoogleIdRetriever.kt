package tv.lumo.android.feature.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Getting a Google ID token out of the platform's account picker (US-03).
 *
 * <h2>Why this is an interface</h2>
 *
 * Credential Manager needs an `Activity` and a live Google Play services, so a
 * ViewModel that called it directly could only be tested on a device. Behind this
 * interface the sign-in logic — which failure means what, what a dismissal means —
 * is ordinary JVM code with a fake.
 *
 * <h2>What this application learns about the user</h2>
 *
 * **One string, and it is not readable here.** The picker runs in the system's
 * process; Lumo never reads the device's account list, never sees a password, and
 * receives only the signed token. Even the email inside it is ignored on this
 * side: the server verifies the signature and reads the address out of the
 * verified token, because an email a client sends is an email anybody can send.
 */
interface GoogleIdRetriever {

    /**
     * Whether an OAuth client is configured in this build at all.
     *
     * False in a fresh checkout, and that is a supported state: `.env` carries no
     * client ID and none may be committed (AGENTS.md §5). The screens draw no
     * Google button when this is false — a button that is certain to fail is
     * worse than no button.
     */
    val configured: Boolean

    /** Shows the picker and waits. Never throws for an outcome the user caused. */
    suspend fun retrieve(activity: Activity): GoogleIdOutcome
}

/**
 * What came back from the picker.
 *
 * [Dismissed] is deliberately not a failure. Closing the sheet is a decision, and
 * a screen that answered it with a red message would be scolding somebody for
 * changing their mind.
 */
sealed interface GoogleIdOutcome {

    /** A signed token, straight from Google, to be handed to the server as-is. */
    data class Token(val idToken: String) : GoogleIdOutcome

    /** The sheet was closed. Nothing to say, nothing to show. */
    data object Dismissed : GoogleIdOutcome

    /** No Google account on this device, or none the user was willing to use. */
    data object NoAccount : GoogleIdOutcome

    /** The picker itself could not run — no provider, no Play services, a fault. */
    data object Unavailable : GoogleIdOutcome
}

@Singleton
class CredentialManagerGoogleIdRetriever @Inject constructor(
    @ApplicationContext private val context: Context,
) : GoogleIdRetriever {

    override val configured: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    override suspend fun retrieve(activity: Activity): GoogleIdOutcome {
        if (!configured) return GoogleIdOutcome.Unavailable

        // The branded-button flow rather than the bottom sheet: this is reached
        // by pressing "Continue with Google", so the full chooser is what the
        // user just asked for. The sheet variant exists for an automatic prompt
        // on arrival, which is a different screen and a different consent.
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build(),
            )
            .build()

        val response = try {
            // The activity, not the application context: this opens a window over
            // the current one, and Credential Manager needs the one it sits on.
            // It is passed per call and never retained.
            CredentialManager.create(context).getCredential(activity, request)
        } catch (cancellation: CancellationException) {
            // The coroutine was cancelled — the screen went away. Not an outcome.
            throw cancellation
        } catch (dismissed: GetCredentialCancellationException) {
            return GoogleIdOutcome.Dismissed
        } catch (none: NoCredentialException) {
            return GoogleIdOutcome.NoAccount
        } catch (failure: GetCredentialException) {
            return GoogleIdOutcome.Unavailable
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            // A provider answered with something that is not a Google token. It
            // cannot be sent, and there is nothing the user did wrong.
            return GoogleIdOutcome.Unavailable
        }

        return try {
            GoogleIdOutcome.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (malformed: GoogleIdTokenParsingException) {
            GoogleIdOutcome.Unavailable
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleIdModule {

    @Binds
    @Singleton
    abstract fun googleIdRetriever(impl: CredentialManagerGoogleIdRetriever): GoogleIdRetriever
}
