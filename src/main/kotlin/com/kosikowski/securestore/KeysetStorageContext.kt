package com.kosikowski.securestore

import android.content.Context
import android.content.ContextWrapper

/**
 * Tink's `AndroidKeysetManager` reads and writes keysets through `Context.getApplicationContext()`.
 * For a device-protected context that is the credential-encrypted application context, so the
 * keysets would live in storage that is unavailable before the first unlock, apart from the data they
 * protect. Returning this wrapper keeps them in the wrapped context's storage.
 */
internal class KeysetStorageContext(
    base: Context,
) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
}
