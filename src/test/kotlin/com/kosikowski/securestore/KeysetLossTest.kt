package com.kosikowski.securestore

import com.google.crypto.tink.shaded.protobuf.InvalidProtocolBufferException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.CharConversionException
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException

class KeysetLossTest {
    private val keyPresent = { true }
    private val keyAbsent = { false }

    @Test
    fun keysetTheMasterKeyNoLongerDecrypts_isLost() {
        assertTrue(AEADBadTagException().isLostKeyset(keyPresent))
    }

    @Test
    fun decryptionFailureWrappedByTheCaller_isLost() {
        assertTrue(IllegalStateException("wrapped", AEADBadTagException()).isLostKeyset(keyPresent))
    }

    @Test
    fun keysetThatNoLongerParses_isLost() {
        assertTrue(InvalidProtocolBufferException("corrupt keyset").isLostKeyset(keyPresent))
    }

    @Test
    fun keysetThatIsNotHex_isLost() {
        assertTrue(CharConversionException("can't read keyset; the pref value is not a valid hex string").isLostKeyset(keyPresent))
    }

    @Test
    fun missingMasterKey_isLost() {
        val tinkFailure = InvalidKeyException("Keystore cannot load the key with ID: android-keystore://alias")

        assertTrue(tinkFailure.isLostKeyset(keyAbsent))
    }

    @Test
    fun invalidKeyExceptionWhileTheMasterKeyExists_isNotLost() {
        assertFalse(InvalidKeyException("Keystore operation failed").isLostKeyset(keyPresent))
    }

    @Test
    fun transientKeystoreFailure_isNotLost() {
        assertFalse(KeyStoreException("Keystore is locked").isLostKeyset(keyAbsent))
    }

    @Test
    fun unrelatedFailure_isNotLost() {
        assertFalse(IOException("disk full").isLostKeyset(keyAbsent))
    }
}
