package io.github.abdullahamr871.neuropad.bridge

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Puts the native `uinput_proto` daemon where the shell user can execute it.
 *
 * NeuroPad 1.0 could only do this with root; everybody else had to push the file from a
 * computer. Now the copy bundled in the APK is installed over the same ADB connection the
 * bridge already uses, so pairing with Wireless debugging is enough to get going.
 */
object DaemonInstaller {
    const val PATH = "/data/local/tmp/uinput_proto"
    private const val ASSET_ARM64 = "uinput_proto"
    private const val ASSET_ARMV7 = "uinput_proto_armv7"
    private const val OK = "NEUROPAD_OK"

    /**
     * The driver build that matches the system's user space: 64-bit ARM where Android runs
     * 64-bit code, 32-bit ARM otherwise (many TVs and TV boxes run a 32-bit Android on a
     * 64-bit CPU, and their kernels may not run 64-bit programs at all).
     */
    fun assetName(supportedAbis: Array<String> = Build.SUPPORTED_ABIS): String =
        if ("arm64-v8a" in supportedAbis) ASSET_ARM64 else ASSET_ARMV7

    class InstallException(message: String) : Exception(message)

    /** @return a short description of what was done, for the log. */
    fun ensureInstalled(context: Context, shell: Shell): String {
        val asset = assetName()
        val bundled = try {
            context.assets.open(asset).use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        val installedSize = installedSize(shell)
        if (bundled == null) {
            if (installedSize > 0) return "using the driver already on the device"
            throw InstallException("The driver is missing from this build. Run the PC installer once.")
        }
        if (installedSize == bundled.size.toLong()) return "driver up to date ($asset)"

        // 1) Copy through shared storage, which both the app and the shell user can reach.
        val staged = stage(context, bundled)
        if (staged != null) {
            val out = shell.run("cp '${staged.absolutePath}' $PATH && chmod 755 $PATH && echo $OK")
            staged.delete()
            if (OK in out && installedSize(shell) == bundled.size.toLong()) return "driver installed ($asset)"
        }
        // 2) Root can read the app's private files directly.
        if (shell.root) {
            val private = File(context.filesDir, asset).apply { writeBytes(bundled) }
            val out = shell.run("cp '${private.absolutePath}' $PATH && chmod 755 $PATH && echo $OK")
            private.delete()
            if (OK in out) return "driver installed (root)"
        } else {
            // 3) Stream the bytes into `cat` over a raw ADB channel.
            try {
                val tmp = "$PATH.new"
                shell.spawn("cat > $tmp", raw = true).use { process ->
                    process.stdin.write(bundled)
                    process.stdin.flush()
                    Thread.sleep(400)
                }
                val out = shell.run("mv $tmp $PATH && chmod 755 $PATH && echo $OK")
                if (OK in out && installedSize(shell) == bundled.size.toLong()) return "driver installed (stream)"
            } catch (_: Exception) {
            }
        }
        if (installedSize > 0) return "could not update the driver, using the installed one"
        throw InstallException(
            "Could not install the driver to $PATH. Run the PC installer once, then try again.",
        )
    }

    private fun installedSize(shell: Shell): Long = try {
        shell.run("wc -c < $PATH 2>/dev/null").lineSequence()
            .mapNotNull { it.trim().toLongOrNull() }.firstOrNull() ?: -1
    } catch (_: Exception) {
        -1
    }

    private fun stage(context: Context, bytes: ByteArray): File? = try {
        context.getExternalFilesDir(null)?.let { dir ->
            File(dir, "uinput_proto").apply { writeBytes(bytes) }
        }
    } catch (_: Exception) {
        null
    }
}
