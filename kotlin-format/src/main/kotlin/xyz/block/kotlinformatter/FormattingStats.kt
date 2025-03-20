package xyz.block.kotlinformatter

data class FormattingStats(
  val configurationTimeMs: Float,
  val formattingTimeMs: Float,
  val reportingTimeMs: Float,
  val formattableCount: Int,
  val blobCount: Int,
  val fileCount: Int,
  val charsProcessed: Long
)