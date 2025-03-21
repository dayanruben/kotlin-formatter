package xyz.block.kotlinformatter

data class FormattingCommandResult(
  val output: String,
  val hasFailure: Boolean,
  val hasFileChanged: Boolean,
  val stats: FormattingStats? = null
)
