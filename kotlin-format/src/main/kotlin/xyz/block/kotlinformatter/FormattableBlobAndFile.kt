package xyz.block.kotlinformatter

/**
 * A formattable that represents both a Git blob and a file that are in sync
 * (i.e. when a file is fully staged with no unstaged changes).
 * This allows for more efficient formatting by reading directly from the file
 * and writing both to the file and Git blob in one operation.
 */
internal class FormattableBlobAndFile(
    private val blob: FormattableBlob,
    private val file: FormattableFile,
) : Formattable {
    override fun name(): String = file.name()

    override fun read(): String {
        return file.read()
    }

    override fun write(content: String) {
        file.write(content)
        blob.write(content)
    }

    fun path() = blob.path
}