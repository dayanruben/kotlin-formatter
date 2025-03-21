package xyz.block.kotlinformatter

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.InputStream

/**
 * Command-line interface to format Kotlin source code files.
 *
 * ## Usage:
 * ```
 * - kotlin-format [OPTIONS] <File1.kt> <File2.kt> ...
 * - kotlin-format --help
 * ```
 */
class Cli(private val inputStream: InputStream = System.`in`) : CliktCommand(name = "kotlin-format") {
  private val files: List<String> by argument(help = HELP_FILES).multiple()
  private val setExitIfChanged: Boolean by
  option("--set-exit-if-changed", help = HELP_SET_EXIT_IF_CHANGED).flag(default = false)
  private val dryRun: Boolean by option("--dry-run", help = HELP_DRY_RUN).flag(default = false)
  private val preCommit: Boolean by option("--pre-commit", help = HELP_PRE_COMMIT).flag(default = false)
  private val prePush: Boolean by option("--pre-push", help = HELP_PRE_PUSH).flag(default = false)
  private val pushCommit: String by option("--push-commit", help = HELP_PUSH_COMMIT).default("HEAD")
  private val stats: Boolean by
  option("--print-stats", help = HELP_PRINT_STATS, envvar = "KOTLIN_FORMATTER_STATS").flag(default = false)
  private val daemon: Boolean by option("--daemon", help = "Start the Kotlin formatter daemon.").flag(default = false)
  private val stopDaemon: Boolean by option("--stop-daemon", help = "Stop the Kotlin formatter daemon.").flag(default = false)

  override fun help(context: Context) = HELP_MESSAGE

  override fun run() {
    if (daemon) {
      Daemon().start()
      return
    }

    if (stopDaemon) {
      Daemon().stop()
      return
    }

    // Validate inputs
    if (files.isEmpty()) {
      throw PrintHelpMessage(this.currentContext)
    }
    if (preCommit && prePush) {
      // These two options are mutually exclusive
      throw PrintMessage("--pre-push and --pre-commit are mutually exclusive", ExitCode.BAD_ARGS, true)
    }
    if (prePush && !dryRun) {
      throw PrintMessage("--pre-push without --dry-run is currently unsupported", ExitCode.BAD_ARGS, true)
    }

    val stdStreamsMode = files.size == 1 && files.contains("-")

    val formatter = KotlinFormatter(
      inputStream = inputStream,
      files = files,
      dryRun = dryRun,
      preCommit = preCommit,
      prePush = prePush,
      pushCommit = pushCommit,
      stats = stats,
      outputCallback = { echo(it, trailingNewline = false) }
    )

    val result = formatter.format()

    // Do not output anything apart from formatted code when in stdStreamsMode
    if (!stdStreamsMode || dryRun) {
      echo(result.output, trailingNewline = false)

      result.stats?.let { stats ->
        echo("Time to configure: ${stats.configurationTimeMs}ms", err = true)
        echo("   Time to format: ${stats.formattingTimeMs}ms", err = true)
        echo("   Time to report: ${stats.reportingTimeMs}ms", err = true)
        echo("Formattable count: ${stats.formattableCount}", err = true)
        echo("       Blob count: ${stats.blobCount}", err = true)
        echo("       File count: ${stats.fileCount}", err = true)
        echo("  Chars processed: ${stats.charsProcessed}", err = true)
      }
    }

    if (result.hasFailure) {
      throw ProgramResult(ExitCode.FAILURE)
    }

    if (setExitIfChanged && result.hasFileChanged) {
      throw ProgramResult(ExitCode.FILE_CHANGED)
    }
  }

  companion object {
    const val HELP_MESSAGE = "Command-line interface to format Kotlin source code files."
    const val HELP_FILES = "Files or directory to format."
    const val HELP_SET_EXIT_IF_CHANGED =
      "Set the program to exit with a non-zero exit code if any files needed to be changed."
    const val HELP_DRY_RUN = "Displays the changes that would be made but does not write the changes to disk."
    const val HELP_PRE_COMMIT =
      "Format staged files as part of the pre-commit process. Mutually exclusive with --pre-push."
    const val HELP_PRE_PUSH =
      "Check committed files as part of the pre-push process. Mutually exclusive with --pre-commit."
    const val HELP_PUSH_COMMIT = "The SHA of the commit to use for pre-push. Defaults to 'HEAD'."
    const val HELP_PRINT_STATS = "Emit performance-related statistics to help diagnose performance issues."
  }
}

fun main(args: Array<String>) = Cli().main(args)