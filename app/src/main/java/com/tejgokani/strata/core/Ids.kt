package com.tejgokani.strata.core

import java.util.UUID

/** Central place for id generation so tests can substitute deterministic ids if needed. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
