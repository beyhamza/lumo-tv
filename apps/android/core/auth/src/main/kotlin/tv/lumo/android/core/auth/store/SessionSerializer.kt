package tv.lumo.android.core.auth.store

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import tv.lumo.android.core.auth.SessionTokens
import tv.lumo.android.core.auth.crypto.KeystoreCipher
import tv.lumo.android.core.auth.crypto.SessionUnreadableException

/**
 * Reads and writes the session as one encrypted blob (docs/architecture.md §5:
 * "Android : DataStore chiffré adossé au Keystore").
 *
 * DataStore gives the atomic write and the single-writer guarantee;
 * [KeystoreCipher] gives the confidentiality. Encrypting the whole record rather
 * than individual preference values means the file leaks nothing at all — not
 * even that a session exists, or which user it belongs to.
 *
 * The payload is a versioned length-prefixed record rather than JSON. It costs
 * nothing, it cannot be misread, and the version byte is what makes it possible
 * to add a field later without every signed-in user being logged out.
 */
class SessionSerializer @Inject constructor(
    private val cipher: KeystoreCipher,
) : Serializer<SessionTokens?> {

    override val defaultValue: SessionTokens? = null

    override suspend fun readFrom(input: InputStream): SessionTokens? {
        val envelope = input.readBytes()
        if (envelope.isEmpty()) return null

        val plaintext = try {
            cipher.decrypt(envelope)
        } catch (e: SessionUnreadableException) {
            // A session we cannot decrypt is a session that no longer exists.
            // Throwing CorruptionException lets DataStore's corruption handler
            // replace the file with an empty one; the user signs in again. The
            // alternative — rethrowing — leaves the app unable to start.
            throw CorruptionException("Session blob is unreadable", e)
        }

        return DataInputStream(plaintext.inputStream()).use { data ->
            when (val version = data.readByte().toInt()) {
                VERSION_1 -> SessionTokens(
                    accessToken = data.readUTF(),
                    refreshToken = data.readUTF(),
                    accessTokenExpiresAt = data.readLong(),
                    userId = data.readUTF(),
                    deviceId = data.readUTF(),
                )

                else -> throw CorruptionException("Unknown session format version $version")
            }
        }
    }

    override suspend fun writeTo(t: SessionTokens?, output: OutputStream) {
        if (t == null) {
            // Signing out truncates the file. No tombstone, nothing to recover.
            output.write(ByteArray(0))
            output.flush()
            return
        }

        val plaintext = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { data ->
                data.writeByte(VERSION_1)
                data.writeUTF(t.accessToken)
                data.writeUTF(t.refreshToken)
                data.writeLong(t.accessTokenExpiresAt)
                data.writeUTF(t.userId)
                data.writeUTF(t.deviceId)
            }
        }.toByteArray()

        output.write(cipher.encrypt(plaintext))
        output.flush()
    }

    private companion object {
        const val VERSION_1 = 1
    }
}
