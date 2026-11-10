package io.github.abdullahamr871.neuropad.bridge

import android.content.Context
import io.github.abdullahamr871.neuropad.adb.AdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A running privileged process with its standard streams. */
interface ShellProcess : Closeable {
    val stdout: InputStream
    val stdin: OutputStream
}

/**
 * Runs commands as the shell user (through the loopback ADB connection) or as root.
 * This is the only place that knows which of the two is in use.
 */
class Shell(private val context: Context, val root: Boolean) {

    /**
     * Starts a long-running command.
     *
     * @param raw ADB only. `true` uses the `exec:` service (a plain socket pair, no terminal:
     * no echo, no CR/LF rewriting). `false` uses `shell:`, the PTY-backed transport that
     * NeuroPad 1.0 used, kept as a fallback for unusual `adbd` builds.
     */
    fun spawn(command: String, raw: Boolean = true): ShellProcess =
        if (root) spawnRoot(command) else spawnAdb(if (raw) "exec:$command" else "shell:exec $command")

    /** Runs a short command and returns everything it printed (stdout, plus stderr over ADB). */
    fun run(command: String, timeoutMs: Long = 6000): String {
        if (root) {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val out = ByteArrayOutputStream()
            val reader = thread(name = "np-su-out") {
                try {
                    process.inputStream.copyTo(out)
                } catch (_: Exception) {
                }
            }
            process.outputStream.close()
            if (!waitFor(process, timeoutMs)) process.destroy()
            reader.join(500)
            return synchronized(out) { out.toString("UTF-8") }.trim()
        }
        val stream = AdbConnectionManager.get(context).openStream("shell:$command")
        val out = ByteArrayOutputStream()
        val reader = thread(name = "np-adb-out") {
            try {
                val input = stream.openInputStream()
                val buf = ByteArray(4096)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    synchronized(out) { out.write(buf, 0, n) }
                }
            } catch (_: Exception) {
            }
        }
        reader.join(timeoutMs)
        closeQuietly(stream)
        reader.join(500)
        return synchronized(out) { out.toString("UTF-8") }.replace("\r", "").trim()
    }

    private fun spawnRoot(command: String): ShellProcess {
        val process = ProcessBuilder("su").start()
        // Drain stderr so a chatty su binary can never block on a full pipe.
        thread(name = "np-su-err", isDaemon = true) {
            try {
                process.errorStream.copyTo(NullOutputStream)
            } catch (_: Exception) {
            }
        }
        process.outputStream.write("exec $command\n".toByteArray())
        process.outputStream.flush()
        return object : ShellProcess {
            override val stdout: InputStream = process.inputStream
            override val stdin: OutputStream = process.outputStream
            override fun close() {
                try {
                    process.outputStream.close()
                } catch (_: Exception) {
                }
                process.destroy()
            }
        }
    }

    private fun spawnAdb(destination: String): ShellProcess {
        val stream: AdbStream = AdbConnectionManager.get(context).openStream(destination)
        return object : ShellProcess {
            override val stdout: InputStream = stream.openInputStream()
            override val stdin: OutputStream = stream.openOutputStream()
            override fun close() = closeQuietly(stream)
        }
    }

    private fun waitFor(process: Process, timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(25)
            }
        }
        return false
    }

    private object NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    companion object {
        fun closeQuietly(closeable: AutoCloseable?) {
            try {
                closeable?.close()
            } catch (_: Exception) {
            }
        }

        /** Asks `su` for a root shell; shows the superuser prompt on first use. */
        fun hasRoot(): Boolean = try {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            process.outputStream.close()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor()
            "uid=0" in text
        } catch (_: Exception) {
            false
        }
    }
}
