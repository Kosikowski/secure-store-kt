package com.kosikowski.securestore

import com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException
import java.security.InvalidKeyException
import javax.crypto.AEADBadTagException

private const val MAX_CAUSE_DEPTH = 10

/**
 * Whether a failure to open the keysets means they can never be opened again.
 *
 * - `AEADBadTagException`: the Keystore master key no longer decrypts the keyset (it was deleted and
 *   recreated).
 * - `InvalidProtocolBufferException`: the keyset no longer parses.
 * - `InvalidKeyException` while the master key alias is absent: Tink does not recreate a missing
 *   master key when a keyset exists, it fails with "Keystore cannot load the key with ID". A transient
 *   Keystore error can surface as `InvalidKeyException` too, hence the check that the key is gone.
 */
internal fun Throwable.isLostKeyset(masterKeyExists: () -> Boolean): Boolean {
    val chain = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
    if (chain.any { it is AEADBadTagException || it is InvalidProtocolBufferException }) return true
    return chain.any { it is InvalidKeyException } && !masterKeyExists()
}
