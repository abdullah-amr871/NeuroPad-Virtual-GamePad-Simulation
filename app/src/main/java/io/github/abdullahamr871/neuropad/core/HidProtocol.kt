package io.github.abdullahamr871.neuropad.core

/**
 * The line-based JSON protocol spoken with the native `uinput_proto` daemon
 * (a drop-in for AOSP's `/system/bin/hid`).
 *
 * Commands are assembled by hand: the hot path sends one line per input report and
 * should not build a JSON tree every few milliseconds.
 */
object HidProtocol {

    fun register(profile: EmulationProfile, uniq: String): String = buildString(1024) {
        append("{\"id\":1,\"command\":\"register\",\"name\":\"").append(profile.deviceName)
        append("\",\"vid\":").append(profile.vendorId)
        append(",\"pid\":").append(profile.productId)
        append(",\"bus\":\"").append(profile.bus).append('"')
        append(",\"descriptor\":")
        appendArray(profile.descriptor)
        if (profile.family == EmulationProfile.Family.PLAYSTATION) {
            append(",\"uniq\":\"").append(uniq).append('"')
            append(",\"feature_reports\":[")
            var first = true
            for ((id, data) in profile.featureReports) {
                if (!first) append(',')
                first = false
                append("{\"id\":").append(id).append(",\"data\":")
                appendArray(data)
                append('}')
            }
            append(']')
        }
        append("}\n")
    }

    fun report(report: IntArray): String = buildString(report.size * 4 + 40) {
        append("{\"id\":1,\"command\":\"report\",\"report\":")
        appendArray(report)
        append("}\n")
    }

    /**
     * Both spellings of the error field are sent: the 1.0 daemon only understood "err"
     * while the 1.0 app only sent "error", so failures were reported to the kernel as success.
     */
    fun getReportReply(requestId: Int, data: IntArray?): String = buildString(256) {
        val error = if (data == null) 1 else 0
        append("{\"command\":\"get_report_reply\",\"id\":").append(requestId)
        append(",\"err\":").append(error).append(",\"error\":").append(error)
        if (data != null) {
            append(",\"report\":")
            appendArray(data)
        }
        append("}\n")
    }

    fun setReportReply(requestId: Int): String =
        "{\"command\":\"set_report_reply\",\"id\":$requestId,\"err\":0}\n"

    private fun StringBuilder.appendArray(values: IntArray) {
        append('[')
        for (i in values.indices) {
            if (i > 0) append(',')
            append(values[i] and 0xFF)
        }
        append(']')
    }

    /**
     * Splits a character stream into top-level JSON objects. The daemon prints one object
     * per line, but ADB may deliver them in arbitrary chunks.
     */
    class ObjectSplitter(private val onObject: (String) -> Unit) {
        private val buffer = StringBuilder()
        private var depth = 0
        private var inString = false
        private var escaped = false

        fun feed(chars: CharArray, length: Int) {
            for (i in 0 until length) {
                val c = chars[i]
                if (depth == 0 && c != '{') continue
                buffer.append(c)
                when {
                    escaped -> escaped = false
                    inString && c == '\\' -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) {
                            onObject(buffer.toString())
                            buffer.setLength(0)
                        }
                    }
                }
                if (buffer.length > MAX_OBJECT_CHARS) {
                    buffer.setLength(0)
                    depth = 0
                    inString = false
                    escaped = false
                }
            }
        }

        private companion object {
            const val MAX_OBJECT_CHARS = 64 * 1024
        }
    }
}
