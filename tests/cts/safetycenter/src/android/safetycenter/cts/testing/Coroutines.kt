/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.safetycenter.cts.testing

import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** A class that facilitates interacting with coroutines. */
// TODO(b/228823159) Consolidate with other Coroutines helper functions
object Coroutines {

    /** Shorthand for [runBlocking] combined with [withTimeout]. */
    fun <T> runBlockingWithTimeout(timeout: Duration = TIMEOUT_LONG, block: suspend () -> T): T =
        runBlocking {
            withTimeout(timeout.toMillis()) { block() }
        }

    /** Shorthand for [runBlocking] combined with [withTimeoutOrNull] */
    fun <T> runBlockingWithTimeoutOrNull(
        timeout: Duration = TIMEOUT_LONG,
        block: suspend () -> T
    ): T? = runBlocking { withTimeoutOrNull(timeout.toMillis()) { block() } }

    /** Check a condition using coroutines with a timeout. */
    fun waitForWithTimeout(
        timeout: Duration = TIMEOUT_LONG,
        checkPeriod: Duration = CHECK_PERIOD,
        condition: () -> Boolean
    ) {
        runBlockingWithTimeout(timeout) { waitFor(checkPeriod, condition) }
    }

    /** Check a condition using coroutines. */
    private suspend fun waitFor(checkPeriod: Duration = CHECK_PERIOD, condition: () -> Boolean) {
        while (!condition()) {
            delay(checkPeriod.toMillis())
        }
    }

    /** A medium period, to be used for conditions that are expected to change. */
    private val CHECK_PERIOD = Duration.ofMillis(250)

    /** A long timeout, to be used for actions that are expected to complete. */
    val TIMEOUT_LONG: Duration = Duration.ofSeconds(15)

    /** A short timeout, to be used for actions that are expected not to complete. */
    val TIMEOUT_SHORT: Duration = Duration.ofMillis(750)
}
