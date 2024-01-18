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
package com.diffplug.selfie.junit5

import kotlin.test.Test
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisableIfTestFails
class BinaryTest : Harness("undertest-junit5") {
  @Test @Order(1)
  fun readFailsBecauseTodo() {
    gradleReadSSFail()
  }

  @Test @Order(2)
  fun writeSucceeds() {
    gradleWriteSS()
  }

  @Test @Order(3)
  fun nowReadSucceeds() {
    gradleReadSS()
  }

  @Test @Order(4)
  fun cleanup() {
    ut_mirror().restoreFromGit()
    ut_snapshot().deleteIfExists()
  }
}
