package com.rynvortex.spddump

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root backend: skips the USB Host API / permission-fd dance entirely and lets the
 * native binary (built the normal libusb-linux way, opening /dev/bus/usb/* directly)
 * run as root. Simpler and a bit more reliable for kicking devices between diag
 * stages, but only works on rooted hosts.
 *
 * The binary itself is the same spd_dump build used in the Termux release - it's
 * bundled as an asset and copied out to the app's native-lib directory (or /data/local/tmp)
 * on first run, then invoked via `su -c`.
 */
class RootBackend(private val binaryPath: String, private val listener: Listener) {

    interface Listener {
        fun onLog(line: String)
        fun onFinished(exitCode: Int)
    }

    fun isRootAvailable(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "id").start()
            p.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs spd_dump under su with the given args, e.g.:
     *   ["--wait", "300", "fdl", fdl1Path, "0x40004000", "fdl", fdl2Path, "0x0", "exec", "reset"]
     */
    fun run(args: List<String>) {
        Thread {
            try {
                val cmd = mutableListOf("su", "-c")
                val joined = (listOf(binaryPath) + args).joinToString(" ") { arg ->
                    if (arg.contains(" ")) "\"$arg\"" else arg
                }
                cmd.add(joined)

                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    listener.onLog(line ?: "")
                }
                val code = process.waitFor()
                listener.onFinished(code)
            } catch (e: Exception) {
                listener.onLog("Root backend error: ${e.message}")
                listener.onFinished(-1)
            }
        }.start()
    }

    companion object {
        /** Copies the bundled spd_dump binary out of assets and chmods it executable. */
        fun extractBinary(context: android.content.Context): String {
            val outFile = File(context.filesDir, "spd_dump")
            if (!outFile.exists()) {
                context.assets.open("spd_dump_arm64").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                outFile.setExecutable(true)
            }
            return outFile.absolutePath
        }
    }
}
