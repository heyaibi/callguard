package com.callguard.spam.classify

import java.io.File

/**
 * Keeps the token model in one file between launches. [load] returns an empty model when the file is
 * missing or does not parse, so a damaged file loses only the learned tokens.
 */
class SmsModelFile(
    private val file: File,
) {
    fun load(): TokenModel = runCatching { TokenModel.fromBytes(file.readBytes()) }.getOrElse { TokenModel.empty() }

    /** Writes a temporary file, then renames it over the model, so a crash never leaves half a model. */
    fun save(model: TokenModel) {
        val temp = File("${file.path}.tmp")
        temp.writeBytes(model.toBytes())
        check(temp.renameTo(file)) { "Could not replace ${file.name}" }
    }
}
