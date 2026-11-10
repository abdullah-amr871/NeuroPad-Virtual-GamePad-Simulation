package io.github.abdullahamr871.neuropad.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds the random ports of this phone's Wireless debugging service through mDNS.
 *
 * Fixes over 1.0:
 *  - services announced by *other* devices on the same Wi-Fi are ignored (1.0 took the
 *    port of any phone with Wireless debugging enabled and then failed to connect);
 *  - resolves are queued, because NsdManager can only run one at a time on Android < 14.
 */
class AdbDiscovery(context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnectPort(port: Int)
        fun onPairingPort(port: Int) {}
    }

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var connectListener: NsdManager.DiscoveryListener? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null
    private val pending = ConcurrentLinkedQueue<Pair<NsdServiceInfo, (Int) -> Unit>>()
    private val resolving = AtomicBoolean(false)

    @Synchronized
    fun start() {
        stop()
        connectListener = discover(TYPE_CONNECT) { listener.onConnectPort(it) }
        pairingListener = discover(TYPE_PAIRING) { listener.onPairingPort(it) }
    }

    @Synchronized
    fun stop() {
        for (l in listOfNotNull(connectListener, pairingListener)) {
            try {
                nsd.stopServiceDiscovery(l)
            } catch (e: Exception) {
                Log.d(TAG, "stopServiceDiscovery: ${e.message}")
            }
        }
        connectListener = null
        pairingListener = null
        pending.clear()
    }

    private fun discover(type: String, onPort: (Int) -> Unit): NsdManager.DiscoveryListener? {
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery start failed for $serviceType: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pending.add(serviceInfo to onPort)
                resolveNext()
            }
        }
        return try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l)
            l
        } catch (e: Exception) {
            Log.w(TAG, "Could not start discovery for $type", e)
            null
        }
    }

    private fun resolveNext() {
        if (!resolving.compareAndSet(false, true)) return
        val (info, onPort) = pending.poll() ?: run {
            resolving.set(false)
            return
        }
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.d(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    done()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = serviceInfo.host
                    if (serviceInfo.port > 0 && isThisDevice(host)) onPort(serviceInfo.port)
                    done()
                }

                private fun done() {
                    resolving.set(false)
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "resolveService threw", e)
            resolving.set(false)
            resolveNext()
        }
    }

    private companion object {
        const val TAG = "NeuroPad/Mdns"
        const val TYPE_CONNECT = "_adb-tls-connect._tcp"
        const val TYPE_PAIRING = "_adb-tls-pairing._tcp"

        fun isThisDevice(host: InetAddress?): Boolean {
            if (host == null) return true // cannot tell; behave like 1.0
            if (host.isLoopbackAddress) return true
            return try {
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .flatMap { it.inetAddresses.asSequence() }
                    .any { it.address.contentEquals(host.address) }
            } catch (e: Exception) {
                true
            }
        }
    }
}
