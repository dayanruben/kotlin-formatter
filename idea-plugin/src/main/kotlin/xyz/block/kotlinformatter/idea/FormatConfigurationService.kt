package xyz.block.kotlinformatter.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import xyz.block.kotlinformatter.idea.FormatOnSavePostStartupActivity.Companion
import java.io.InputStreamReader
import java.util.Properties

@Service(Service.Level.PROJECT)
class FormatConfigurationService(private val project: Project) {

  // This is calculated once and on demand.
  // Changes to the config file will require a restart.
  val formattingEnabled by lazy {
    val configFile = project.getFile(CONFIG_FILE_PATH)
    val enabled = configFile?.let { file ->
      val properties = Properties()
      InputStreamReader(file.inputStream).use {
        properties.load(it)
        properties.getProperty(ENABLED_PROPERTY_NAME)?.toBoolean() == true
      }
    } ?: false

    logger.info("Formatting enabled: $enabled")
    enabled
  }

  companion object {
    private const val CONFIG_FILE_PATH: String = ".idea/kotlin-formater.properties"
    private const val ENABLED_PROPERTY_NAME: String = "kotlin-formatter.enabled"
    private val logger = Logger.getInstance(FormatConfigurationService::class.java.name)
  }
}