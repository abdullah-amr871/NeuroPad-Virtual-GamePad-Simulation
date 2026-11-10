package io.github.abdullahamr871.neuropad.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import io.github.abdullahamr871.neuropad.core.XInputParser
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Talks to the physical gamepad through the Android USB host API.
 *
 * The XInput interface is claimed with `force = true`, which detaches the kernel's own
 * `xpad` driver. That is what stops games from seeing the controller twice, and Android
 * re-attaches the driver by itself when the interface is released.
 *
 * NeuroPad 1.0 additionally tried to hide the pad by deleting `/dev/input/event*` nodes
 * that merely *looked* like a gamepad (root only). That could remove unrelated input
 * devices until the next reboot and is intentionally not part of this rewrite.
 */
class UsbGamepadManager(context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnected(device: UsbDevice, name: String)
        fun onDisconnected()
        /** Called on the polling thread for every packet received from the pad. */
        fun onPacket(data: ByteArray, length: Int)
        fun onMessage(message: String)
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val lock = Any()

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var pollThread: Thread? = null
    @Volatile private var polling = false
    private var registered = false

    val isConnected: Boolean get() = synchronized(lock) { connection != null }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dev: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    // The extra can be missing on some builds; fall back to a fresh scan.
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ||
                        (dev != null && usbManager.hasPermission(dev))
                    if (granted) {
                        if (dev != null) connect(dev) else scanAndConnect()
                    } else {
                        listener.onMessage("USB permission denied")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                    if (dev != null && isCompatible(dev) && !isConnected) {
                        if (usbManager.hasPermission(dev)) connect(dev) else requestPermission(dev)
                    }
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    if (dev != null && dev.deviceId == synchronized(lock) { device?.deviceId }) {
                        listener.onMessage("Gamepad unplugged")
                        disconnect()
                    }
            }
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // All three broadcasts come from the system (the permission result through our own
        // package-scoped PendingIntent), so the receiver does not need to be exported.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        registered = false
    }

    /** Looks for a compatible pad that is already plugged in. */
    fun scanAndConnect() {
        if (isConnected) return
        val devices = usbManager.deviceList.values
        listener.onMessage("Scanning ${devices.size} USB device(s)")
        for (dev in devices) {
            listener.onMessage(
                "Found ${dev.productName ?: "unknown"} VID=0x%04X PID=0x%04X".format(dev.vendorId, dev.productId),
            )
            if (isCompatible(dev)) {
                if (usbManager.hasPermission(dev)) connect(dev) else requestPermission(dev)
                return
            }
        }
        listener.onMessage("No compatible gamepad found. Plug it in and it will be picked up.")
    }

    private fun requestPermission(dev: UsbDevice) {
        listener.onMessage("Asking for USB permission")
        // Explicit (package-scoped) intent: Android 14 rejects a mutable PendingIntent that
        // wraps an implicit intent, which is what 1.0 created here.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        usbManager.requestPermission(dev, PendingIntent.getBroadcast(appContext, 0, intent, flags))
    }

    private fun connect(dev: UsbDevice) {
        synchronized(lock) {
            if (connection != null) return
            val conn = try {
                usbManager.openDevice(dev)
            } catch (e: Exception) {
                null
            }
            if (conn == null) {
                listener.onMessage("Could not open the USB device")
                return
            }
            val picked = pickInterface(dev)
            if (picked == null) {
                conn.close()
                listener.onMessage("The device has no usable input endpoint")
                return
            }
            if (!conn.claimInterface(picked.first, true)) {
                conn.close()
                listener.onMessage("Could not claim the gamepad's USB interface")
                return
            }
            device = dev
            connection = conn
            usbInterface = picked.first
            endpointIn = picked.second
            endpointOut = picked.third
        }
        val name = displayName(dev)
        listener.onMessage("Connected to $name" + if (endpointOut == null) " (no rumble endpoint)" else "")
        listener.onConnected(dev, name)
        startPolling()
    }

    /**
     * Prefers the XInput control interface (vendor class 0xFF, subclass 0x5D, protocol 1).
     * Otherwise falls back to the first interface that has an IN endpoint; 1.0 took the
     * *last* interface with any endpoint, which on composite devices is rarely the gamepad.
     */
    private fun pickInterface(dev: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? {
        var fallback: Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? = null
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT && ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    if (epIn == null) epIn = ep
                } else if (epOut == null) {
                    epOut = ep
                }
            }
            if (epIn == null) continue
            val candidate = Triple(intf, epIn, epOut)
            val isXInput = intf.interfaceClass == 0xFF && intf.interfaceSubclass == XINPUT_SUBCLASS &&
                intf.interfaceProtocol == 1
            if (isXInput) return candidate
            if (fallback == null) fallback = candidate
        }
        return fallback
    }

    private fun startPolling() {
        polling = true
        pollThread = thread(name = "NeuroPad-USB", priority = Thread.MAX_PRIORITY) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val conn = synchronized(lock) { connection }
            val ep = synchronized(lock) { endpointIn }
            if (conn == null || ep == null) return@thread
            val buffer = ByteArray(maxOf(ep.maxPacketSize, 64))
            while (polling) {
                val n = try {
                    conn.bulkTransfer(ep, buffer, buffer.size, 100)
                } catch (e: Exception) {
                    -1
                }
                if (n > 0) {
                    listener.onPacket(buffer, n)
                } else if (n < 0 && polling) {
                    // Timeout (the pad only reports changes) or error; either way do not spin.
                    try {
                        Thread.sleep(1)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    /** Drives the two rumble motors of the physical pad (0..255 each). */
    fun writeRumble(left: Int, right: Int): Boolean {
        val conn: UsbDeviceConnection
        val ep: UsbEndpoint
        synchronized(lock) {
            conn = connection ?: return false
            ep = endpointOut ?: return false
        }
        val packet = XInputParser.rumblePacket(left, right)
        return try {
            synchronized(conn) { conn.bulkTransfer(ep, packet, packet.size, 100) } >= 0
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect() {
        val wasConnected: Boolean
        polling = false
        val t = pollThread
        pollThread = null
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(400)
            } catch (_: InterruptedException) {
            }
        }
        synchronized(lock) {
            wasConnected = connection != null
            try {
                usbInterface?.let { connection?.releaseInterface(it) }
            } catch (_: Exception) {
            }
            try {
                connection?.close()
            } catch (_: Exception) {
            }
            connection = null
            usbInterface = null
            endpointIn = null
            endpointOut = null
            device = null
        }
        if (wasConnected) listener.onDisconnected()
    }

    companion object {
        const val ACTION_USB_PERMISSION = "io.github.abdullahamr871.neuropad.USB_PERMISSION"
        private const val XINPUT_SUBCLASS = 0x5D

        /** Vendors whose pads are known to speak XInput (Microsoft, GameSir, Logitech, ...). */
        private val KNOWN_VENDORS = setOf(
            0x045E, 0x3537, 0x046D, 0x0738, 0x0E6F, 0x24C6, 0x0F0D, 0x1532, 0x1038, 0x044F,
            0x2DC8, 0x146B, 0x1206, 0x2F24,
        )
        private const val VENDOR_GAMESIR = 0x3537
        private val NAME_HINTS = listOf("xbox", "360", "controller", "gamepad", "2.4g")

        fun displayName(dev: UsbDevice): String =
            listOfNotNull(dev.manufacturerNameOrNull(), dev.productNameOrNull())
                .joinToString(" ").ifBlank { "USB gamepad" }

        private fun UsbDevice.productNameOrNull(): String? = try {
            productName?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }

        private fun UsbDevice.manufacturerNameOrNull(): String? = try {
            manufacturerName?.trim()?.takeIf { it.isNotEmpty() && productName?.contains(it, true) != true }
        } catch (_: Exception) {
            null
        }

        /**
         * Acceptance rules, in order: an XInput interface, a GameSir device, a known gamepad
         * vendor, or a telling product name.
         *
         * 1.0 accepted *any* device from the vendor list, so a Logitech or Razer mouse was
         * treated as a gamepad and had its interface claimed. Vendors that also make other
         * peripherals now need a vendor-specific interface, and hubs are never accepted.
         */
        fun isCompatible(dev: UsbDevice): Boolean {
            if (dev.deviceClass == UsbConstants.USB_CLASS_HUB) return false
            var hasVendorInterface = false
            for (i in 0 until dev.interfaceCount) {
                val intf = dev.getInterface(i)
                if (intf.interfaceClass == 0xFF) {
                    hasVendorInterface = true
                    if (intf.interfaceSubclass == XINPUT_SUBCLASS &&
                        (intf.interfaceProtocol == 1 || intf.interfaceProtocol == 129)
                    ) return true
                }
            }
            val product = (dev.productNameOrNull() ?: "").lowercase(Locale.ROOT)
            val maker = try {
                (dev.manufacturerName ?: "").lowercase(Locale.ROOT)
            } catch (_: Exception) {
                ""
            }
            if (dev.vendorId == VENDOR_GAMESIR || "gamesir" in maker || "gamesir" in product || "g8" in product) {
                return true
            }
            if (dev.vendorId in KNOWN_VENDORS && hasVendorInterface) return true
            return NAME_HINTS.any { it in product } || "xbox" in maker
        }

        fun findCompatible(context: Context): UsbDevice? {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return manager.deviceList.values.firstOrNull(::isCompatible)
        }
    }
}
