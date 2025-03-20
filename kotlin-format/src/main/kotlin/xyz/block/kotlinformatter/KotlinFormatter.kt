/*
 * Copyright 2024 (c) 2024 Block, Inc.
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
package xyz.block.kotlinformatter

import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.stream.Stream
import kotlin.streams.toList
import xyz.block.kotlinformatter.TriggerFormatter.Companion.FormattingResult



class KotlinFormatter(
  private val files: List<String>,
  private val dryRun: Boolean = false,
  private val preCommit: Boolean = false,
  private val prePush: Boolean = false,
  private val pushCommit: String = "HEAD",
  private val stats: Boolean = false,
  private val inputStream: InputStream = System.`in`,
  private val outputCallback: (String) -> Unit = { println(it) }
) {

  fun format(): FormattingCommandResult {
    // 1. Validate inputs
    if (files.isEmpty()) {
      throw IllegalArgumentException("No files to format")
    }

    if (preCommit && prePush) {
      throw IllegalArgumentException("--pre-push and --pre-commit are mutually exclusive")
    }

    val timeStart = Instant.now()
    val stdStreamsMode = files.size == 1 && files.contains("-")

    // 2. Create configs from args
    val formattingConfigs =
      if (stdStreamsMode) {
        FormattingConfigs.forStdStreams(inputStream, outputCallback)
      } else if (preCommit) {
        FormattingConfigs.forPreCommit(files, dryRun)
      } else if (prePush) {
        FormattingConfigs.forPrePush(files, dryRun, pushCommit)
      } else {
        FormattingConfigs.forWorkingDir(files, dryRun)
      }

    val timeConfigged = Instant.now()

    // 3. Trigger the formatter
    val formatter = TriggerFormatter(Ktfmt())

    val formattingResults =
      formattingConfigs.formattables
        .parallelStream()
        .map { formattable -> formatter.format(formattable, formattingConfigs) }
        .toSortedSet()

    val timeFormatted = Instant.now()

    // Making a single string and outputting it once is much faster than calling outputCallback for each
    // result individually
    val output = StringBuilder()

    if (formattingConfigs.formattables.isEmpty()) {
      output.appendLine("Nothing to format")
    }

    var hasFailure = false
    var hasFileChanged = false

    formattingResults.forEach { formattingResult ->
      when (formattingResult) {
        is FormattingResult.AlreadyFormatted -> Unit
        is FormattingResult.WouldFormat -> {
          output.appendLine("🛠️ Would format ${formattingResult.fileName}")
          hasFileChanged = true
        }

        is FormattingResult.Formatted -> {
          output.appendLine("✅ Formatted ${formattingResult.fileName}")
          hasFileChanged = true
        }

        is FormattingResult.FormattingError -> {
          output.appendLine("⛔️ ${formattingResult.message}")
          hasFailure = true
        }
      }
    }

    if (hasFileChanged && prePush) {
      output.appendLine(
        "⚠️ The committed files have formatting errors. Please format the files and commit the formatting changes."
      )
    }

    val timeEnd = Instant.now()

    val formattingStats = if (stats) {
      FormattingStats(
        configurationTimeMs = Duration.between(timeStart, timeConfigged).toNanos() / 1.0e6f,
        formattingTimeMs = Duration.between(timeConfigged, timeFormatted).toNanos() / 1.0e6f,
        reportingTimeMs = Duration.between(timeFormatted, timeEnd).toNanos() / 1.0e6f,
        formattableCount = formattingConfigs.formattables.size,
        blobCount = formattingConfigs.formattables.filterIsInstance<FormattableBlob>().size,
        fileCount = formattingConfigs.formattables.filterIsInstance<FormattableFile>().size,
        charsProcessed = formatter.charsProcessed()
      )
    } else null

    return FormattingCommandResult(
      output = output.toString(),
      hasFailure = hasFailure,
      hasFileChanged = hasFileChanged,
      stats = formattingStats
    )
  }

  // Stream doesn't have a toSortedSet() method, so we need to convert it to a list first.
  private fun <E : Comparable<E>> Stream<E>.toSortedSet() = toList().toSortedSet()
}