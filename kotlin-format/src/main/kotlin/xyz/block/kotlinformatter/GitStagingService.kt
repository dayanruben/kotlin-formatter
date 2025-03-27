package xyz.block.kotlinformatter

import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension

/** Provides services related to managing and querying the staging area in Git. */
internal object GitStagingService {
  fun getUnstagedFiles(): Set<Path> {
    return GitProcessRunner.run("diff", "--name-only")
      .lines()
      .map { line -> Path(line) }
      .filter { path -> path.extension == "kt" }
      .toSet()
  }

  fun getStagedFiles(): Set<Path> {
    // the --diff-filter=d filters out deleted files.
    return GitProcessRunner.run("diff", "--name-only", "--cached", "--diff-filter=d")
      .lines()
      .map { line -> Path(line) }
      .filter { path -> path.extension == "kt" }
      .toSet()
  }

  fun getStagedFormattableObjects(gitRoot: Path, pathFilters: List<Path>): List<Formattable> {
    val formattables = mutableListOf<Formattable>()
    GitProcessRunner.run("status", "--porcelain=2", "--untracked-files=no", workingDir = gitRoot.toFile()).trim().lines().forEach { line ->
      val type = line[0]
      // Only handle modifications or moves; we don't care about unmerged or untracked files
      if (type == '1' || type == '2') {
        val tokens = line.split(" ", limit = 9)
        val modificationType = tokens[1]
        val stagedModificationType = modificationType[0]
        val unstagedModificationType = modificationType[1]
        val modeForIndex = tokens[4]
        val hashForIndex = tokens[7]
        val path = Path(tokens[8])

        if (stagedModificationType in setOf('A', 'C', 'M', 'R') && path.extension == "kt") {
          if (pathFilters.isEmpty() || pathFilters.any { path.startsWith(it) }) {
            val blob = FormattableBlob(path, modeForIndex, hashForIndex)
            if (unstagedModificationType == '.') {
              val absolutePath = gitRoot.resolve(path).normalize()
              formattables.add(FormattableBlobAndFile(
                blob = blob,
                file = FormattableFile(absolutePath.toFile(), gitRoot),
              ))
            } else {
              formattables.add(blob)
            }
          }
        }
      }
    }
    return formattables
  }

  /** Retrieves a list of [FormattableBlob]s for files that are staged without any merge conflicts. */
  fun getStagedFormattableBlobs(gitRoot: File, stagedPaths: Set<Path>): List<FormattableBlob> {
    val whitespace = Regex("\\s+")
    return GitProcessRunner.run("ls-files", "--stage", workingDir = gitRoot).trim().lines().mapNotNull { line ->
      val tokens = line.split(whitespace, limit = 4)
      val mode = tokens[0]
      val hash = tokens[1]
      val stageNumber = tokens[2]
      val path = Path(tokens[3])

      // Ignore files in a merge conflict (stageNumber != "0")
      if (path in stagedPaths && stageNumber == "0") {
        FormattableBlob(path, mode, hash)
      } else {
        null
      }
    }
  }

  fun getCommittedBlobs(gitRoot: File, commitRef: String): List<FormattableBlob> {
    val whitespace = Regex("\\s+")
    return GitProcessRunner.run("ls-tree", "-r", commitRef, workingDir = gitRoot).trim().lines().mapNotNull { line ->
      val tokens = line.split(whitespace, limit = 4)
      val mode = tokens[0]
      val type = tokens[1]
      val hash = tokens[2]
      val path = Path(tokens[3])

      // Ignore anything that's not a kotlin file
      if (type == "blob" && path.extension == "kt") {
        FormattableBlob(path, mode, hash)
      } else {
        null
      }
    }
  }
}
