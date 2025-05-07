package xyz.block.kotlinformatter

import xyz.block.kotlinformatter.TriggerFormatter.Companion.FormattingResult
import java.io.File
import kotlin.io.path.name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TriggerFormatterTest {
  // Replacing 4 spaces with 2 spaces
  private val testFormatter =
    object : Formatter {
      override fun format(code: String): String {
        return code.replace("    ", "  ")
      }
    }

  private val unformattedCode =
    """
    |data class Test(
    |    val a: Int,
    |    val b: Int
    |)"""
      .trimMargin()

  private val formattedCode =
    """
    |data class Test(
    |  val a: Int,
    |  val b: Int
    |)"""
      .trimMargin()

  private fun setupTestFile(rootDir: File, originalFileContent: String): File {
    val file = File(rootDir, "Test.kt")
    file.writeText(originalFileContent)

    GitProcessRunner.run("init")
    GitProcessRunner.run("add", file.path)

    return file
  }

  @Test
  fun `formats a file without staging changes`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, unformattedCode)

      val configs = FormattingConfigs.forWorkingDir(listOf(file.toString()))
      val formattingResult = configs.formattables.map { TriggerFormatter(testFormatter).format(it, configs) }.toSet()

      assertThat(formattingResult).containsExactly(FormattingResult.Formatted(file.toString()))
      assertThat(file.readText()).isEqualTo(formattedCode)
      // Check that change is not staged
      assertThat(GitStagingService.getUnstagedFiles().map { it.name }).containsExactly("Test.kt")
    }
  }

  @Test
  fun `does not format an already formatted file`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, formattedCode)

      val configs = FormattingConfigs.forWorkingDir(listOf(file.toString()))
      val formattingResult = configs.formattables.map { TriggerFormatter(testFormatter).format(it, configs) }.toSet()

      assertThat(formattingResult).containsExactly(FormattingResult.AlreadyFormatted(file.toString()))
      assertThat(file.readText()).isEqualTo(formattedCode)
    }
  }

  @Test
  fun `stage formatting change`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, unformattedCode)

      val configs = FormattingConfigs.forPreCommit(listOf())
      val formattingResult = configs.formattables.map { TriggerFormatter(testFormatter).format(it, configs) }.toSet()
      val stagedChange = TestFixtures.getStagedContent(tempDir)

      assertThat(formattingResult).containsExactly(FormattingResult.Formatted(file.name))
      assertThat(file.readText()).isEqualTo(formattedCode)
      assertThat(stagedChange.values).containsExactly(formattedCode)
    }
  }

  @Test
  fun `format staged blob only for file with staged and unstaged changes`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, unformattedCode)
      val workingDirContent = "blah blah blah"
      file.writeText(workingDirContent)

      val configs = FormattingConfigs.forPreCommit(listOf())
      val formattingResult = configs.formattables.map { TriggerFormatter(testFormatter).format(it, configs) }.toSet()
      val stagedChange = TestFixtures.getStagedContent(tempDir)

      assertThat(formattingResult).containsExactly(FormattingResult.Formatted(file.name))
      // note here the working dir file is not modified by the formatter
      assertThat(file.readText()).isEqualTo(workingDirContent)
      // but the staged file is formatted
      assertThat(stagedChange.values).containsExactly(formattedCode)
    }
  }

  @Test
  fun `does not format or stage change in dry run mode`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, unformattedCode)

      val configs = FormattingConfigs.forWorkingDir(listOf(file.toString()), dryRun = true)
      val formattingResult = configs.formattables.map { TriggerFormatter(testFormatter).format(it, configs) }.toSet()
      val stagedChange = TestFixtures.getStagedContent(tempDir)

      assertThat(formattingResult).containsExactly(FormattingResult.WouldFormat(file.toString()))
      assertThat(file.readText()).isEqualTo(unformattedCode)
      assertThat(stagedChange.values).containsExactly(unformattedCode)
    }
  }

  @Test
  fun `reports error if when formatting fails`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      val file = setupTestFile(tempDir, unformattedCode)

      val configs = FormattingConfigs.forWorkingDir(listOf(file.toString()))
      val formattingException = RuntimeException("Formatter failed")
      val failingFormatter =
        object : Formatter {
          override fun format(code: String): String {
            throw formattingException
          }
        }
      val formattingResult = configs.formattables.map { TriggerFormatter(failingFormatter).format(it, configs) }.toSet()

      val expectedErrorMessage = "Unexpected error formatting ${file}: Formatter failed"
      assertThat(formattingResult)
        .containsExactly(FormattingResult.FormattingError(file.path, expectedErrorMessage, formattingException))
      assertThat(file.readText()).isEqualTo(unformattedCode)
    }
  }

  @Test
  fun `handles moved files correctly between pre-commit and pre-push`(@TempDir tempDir: File) {
    TestUtils.withWorkingDir(tempDir) {
      // 1. Create and commit an unformatted file
      val originalFile = setupTestFile(tempDir, unformattedCode)
      TestUtils.setupGitUser()
      GitProcessRunner.run("commit", "-m", "Initial commit with unformatted file")

      // 2. Move the file using Git commands
      val newFileName = "Test2.kt"
      GitProcessRunner.run("mv", originalFile.path, newFileName)

      // 3. pre-commit check should format
      val preCommitConfigs = FormattingConfigs.forPreCommit(listOf())
      val preCommitResult = preCommitConfigs.formattables.map { TriggerFormatter(testFormatter).format(it, preCommitConfigs) }.toSet()
      assertThat(preCommitResult).containsExactly(FormattingResult.Formatted(newFileName))

      // 4. Commit the move
      GitProcessRunner.run("commit", "-m", "Move file")

      // 5. pre-push check should detect already formatted
      val prePushConfigs = FormattingConfigs.forPrePush(listOf(), dryRun = true, commitRef = "HEAD")
      val prePushResult = prePushConfigs.formattables.map { TriggerFormatter(testFormatter).format(it, prePushConfigs) }.toSet()
      assertThat(prePushResult).containsExactly(FormattingResult.AlreadyFormatted(newFileName))
    }
  }
}
