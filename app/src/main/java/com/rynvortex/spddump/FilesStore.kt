package com.rynvortex.spddump

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Mirrors UniTools' internalfiles/input and internalfiles/backup layout:
 * imported .img/.bin/.xml files live in one folder, partition dumps the app
 * creates (backups) live in another, and both show up as pickable lists
 * from the Operations tab.
 */
object FilesStore {

    private fun inputDir(context: Context): File =
        File(context.filesDir, "input").apply { mkdirs() }

    private fun backupDir(context: Context): File =
        File(context.filesDir, "backup").apply { mkdirs() }

    fun listInputFiles(context: Context): List<File> =
        inputDir(context).listFiles()?.sortedBy { it.name } ?: emptyList()

    fun listBackupFiles(context: Context): List<File> =
        backupDir(context).listFiles()?.sortedBy { it.name } ?: emptyList()

    fun backupPathFor(context: Context, name: String): String =
        File(backupDir(context), name).absolutePath

    /** Copies a file picked via SAF into internalfiles/input/. */
    fun importToInput(context: Context, uri: Uri, displayName: String): File {
        val dest = File(inputDir(context), displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun delete(file: File): Boolean = file.delete()
}
