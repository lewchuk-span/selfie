/*
 * Copyright (C) 2024 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.selfie.coroutines

import com.diffplug.selfie.Camera
import com.diffplug.selfie.Mode
import com.diffplug.selfie.Roundtrip
import com.diffplug.selfie.Selfie
import com.diffplug.selfie.Snapshot
import com.diffplug.selfie.guts.CoroutineDiskStorage
import com.diffplug.selfie.guts.DiskSnapshotTodo
import com.diffplug.selfie.guts.DiskStorage
import com.diffplug.selfie.guts.LiteralString
import com.diffplug.selfie.guts.LiteralValue
import com.diffplug.selfie.guts.recordCall
import kotlin.coroutines.coroutineContext

/**
 * Whenever Selfie is called from within a coroutine, you should use
 * `com.diffplug.selfie.coroutines` instead of `com.diffplug.selfie.Selfie`. If you want more
 * details, see the [threading details](http://localhost:3000/jvm/kotest#threading-details).
 */
private suspend fun disk() =
    coroutineContext[CoroutineDiskStorage]?.disk
        ?: throw IllegalStateException(
            """
      No Kotest test is in progress on this coroutine.
      If this is a Kotest test, make sure you added `SelfieExtension` to your `AbstractProjectConfig`:
        +class MyProjectConfig : AbstractProjectConfig() {
        +  override fun extensions() = listOf(SelfieExtension(this))
        +}
      If this is a JUnit test, make the following change:
        -import com.diffplug.selfie.coroutines.expectSelfie
        +import com.diffplug.selfie.Selfie.expectSelfie
      For more info https://selfie.dev/jvm/kotest#selfie-and-coroutines
    """
                .trimIndent())
suspend fun <T> expectSelfie(actual: T, camera: Camera<T>) = expectSelfie(camera.snapshot(actual))
suspend fun expectSelfie(actual: String) = expectSelfie(Snapshot.of(actual))
suspend fun expectSelfie(actual: ByteArray) = expectSelfie(Snapshot.of(actual))
suspend fun expectSelfie(actual: Snapshot) = Selfie.DiskSelfie(actual, disk())
suspend fun preserveSelfiesOnDisk(vararg subsToKeep: String) {
  val disk = disk()
  if (subsToKeep.isEmpty()) {
    disk.keep(null)
  } else {
    subsToKeep.forEach { disk.keep(it) }
  }
}
suspend fun memoize(toMemoize: suspend () -> String) = memoize(Roundtrip.identity(), toMemoize)
suspend fun <T> memoize(roundtrip: Roundtrip<T, String>, toMemoize: suspend () -> T) =
    StringMemoSuspend<T>(disk(), roundtrip, toMemoize)

class StringMemoSuspend<T>(
    private val disk: DiskStorage,
    private val roundtrip: Roundtrip<T, String>,
    private val generator: suspend () -> T
) {
  suspend fun toMatchDisk(sub: String = ""): T {
    val call = recordCall(false)
    if (Selfie.system.mode.canWrite(false, call, Selfie.system)) {
      val actual = generator()
      disk.writeDisk(Snapshot.of(roundtrip.serialize(actual)), sub, call)
      return actual
    } else {
      val snapshot =
          disk.readDisk(sub, call)
              ?: throw Selfie.system.fs.assertFailed(Selfie.system.mode.msgSnapshotNotFound())
      if (snapshot.subject.isBinary || snapshot.facets.isNotEmpty()) {
        throw Selfie.system.fs.assertFailed(
            "Expected a string subject with no facets, got ${snapshot}")
      }
      return roundtrip.parse(snapshot.subject.valueString())
    }
  }
  suspend fun toMatchDisk_TODO(sub: String = ""): T {
    val call = recordCall(false)
    if (Selfie.system.mode.canWrite(true, call, Selfie.system)) {
      val actual = generator()
      disk.writeDisk(Snapshot.of(roundtrip.serialize(actual)), sub, call)
      Selfie.system.writeInline(DiskSnapshotTodo.createLiteral(), call)
      return actual
    } else {
      throw Selfie.system.fs.assertFailed("Can't call `toMatchDisk_TODO` in ${Mode.readonly} mode!")
    }
  }
  suspend fun toBe_TODO(unusedArg: Any? = null): T {
    val call = recordCall(false)
    val writable = Selfie.system.mode.canWrite(true, call, Selfie.system)
    if (writable) {
      val actual = generator()
      Selfie.system.writeInline(
          LiteralValue(null, roundtrip.serialize(actual), LiteralString), call)
      return actual
    } else {
      throw Selfie.system.fs.assertFailed("Can't call `toBe_TODO` in ${Mode.readonly} mode!")
    }
  }
  fun toBe(expected: String): T = roundtrip.parse(expected)
}
