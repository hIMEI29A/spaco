package com.spaco.app.core.data

import java.util.UUID

/**
 * Generates identifiers. Injected rather than called statically so tests can
 * make ids deterministic and assert exactly which records were written.
 */
fun interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
