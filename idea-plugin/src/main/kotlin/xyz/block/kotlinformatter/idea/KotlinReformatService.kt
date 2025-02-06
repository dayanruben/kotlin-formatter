package xyz.block.kotlinformatter.idea

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import xyz.block.kotlinformatter.Ktfmt
import xyz.block.kotlinformatter.idea.KotlinReformatService.Companion.FORMATTING_IGNORE_FILE

/**
 * A service that overrides the default IntelliJ formatting behavior for Kotlin files.
 *
 * Unless the given file is part of [FORMATTING_IGNORE_FILE]
 */
class KotlinReformatService : AsyncDocumentFormattingService() {
  override fun getFeatures(): MutableSet<FormattingService.Feature> {
    return mutableSetOf()
  }

  /**
   * If this is false, the default IntelliJ formatting behavior will be applied and createFormattingTask will not be
   * called.
   */
  override fun canFormat(file: PsiFile): Boolean {
    if (!file.project.getService(FormatConfigurationService::class.java).formattingEnabled) {
      LOG.info("Formatting is not enabled")
      return false
    }

    if (!file.name.endsWith(".kt")) return false

    return !isFormattingIgnored(file)
  }

  override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
    return object : FormattingTask {
      override fun run() {
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            val formattedCode = Ktfmt().format(request.documentText)
            request.onTextReady(formattedCode)
          } catch (e: Exception) {
            // If an error occurs, notify IntelliJ
            request.onError("Formatting Error", e.message ?: "Unknown error")
          }
        }
      }

      override fun cancel(): Boolean {
        return true
      }
    }
  }

  override fun getNotificationGroupId(): String {
    return "Reformat Kotlin Files"
  }

  override fun getName(): String {
    return "Reformat Kotlin Files"
  }

  /**
   * Determines whether formatting should be ignored for the given module based on [FORMATTING_IGNORE_FILE].
   *
   * The [FORMATTING_IGNORE_FILE] contains a list of module names where formatting should be disabled.
   *
   * #### Example Project Structure:
   * ```
   * project-root
   * ├── module1
   * │   └── src
   * ├── module2
   * │   └── src
   * ├── module3
   * │   └── src
   * ```
   *
   * #### Example [FORMATTING_IGNORE_FILE] Content:
   * ```
   * module2
   * module3
   * ```
   *
   * In this example, any file located within `module2` or `module3` will be ignored by the formatter.
   *
   */
  private fun isFormattingIgnored(file: PsiFile): Boolean {
    val filePath = file.viewProvider.virtualFile.path
    val basePath = file.project.basePath ?: return false

    // Check if the file path starts with the base path
    if (!filePath.startsWith(basePath)) return false

    // Extract the module name from the file path
    val module = filePath.substring(basePath.length + 1).split("/").firstOrNull() ?: return false

    val formattingIgnoreModules = file.project.getFileContent(FORMATTING_IGNORE_FILE)?.lines()?.toSet().orEmpty()

    if (formattingIgnoreModules.contains(module)) {
      LOG.info("File in formatting ignore list: $module")
      return true
    }

    return false
  }

  companion object {
    private val LOG = Logger.getInstance(KotlinReformatService::class.java)
    private const val FORMATTING_IGNORE_FILE = ".kotlin-formatter-ignore"
  }
}
