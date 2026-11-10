package io.github.abdullahamr871.neuropad.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.conscrypt.Conscrypt
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * ADB client that connects to the phone's own `adbd` over loopback. The shell user it
 * yields (uid 2000) may open `/dev/uhid`, which a normal app may not.
 *
 * The RSA key pair is generated once and kept in the app's private storage; the phone
 * remembers it after pairing, exactly like it remembers a computer.
 */
class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
    private lateinit var key: PrivateKey
    private lateinit var cert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        loadOrCreateKeys(context)
    }

    private fun loadOrCreateKeys(context: Context) {
        val keyFile = File(context.filesDir, "adbkey")
        val certFile = File(context.filesDir, "adbkey.crt")
        if (keyFile.exists() && certFile.exists()) {
            try {
                key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Stored ADB key unreadable, generating a new one", e)
            }
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val provider = BouncyCastleProvider()
        val subject = X500Name("CN=NeuroPad")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            Date(now - DAY_MS),
            Date(now + 10L * 365 * DAY_MS),
            subject,
            pair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(pair.private)
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(signer))
        keyFile.writeBytes(pair.private.encoded)
        certFile.writeBytes(certificate.encoded)
        key = pair.private
        cert = certificate
    }

    override fun getPrivateKey(): PrivateKey = key
    override fun getCertificate(): Certificate = cert
    override fun getDeviceName(): String = "NeuroPad"

    companion object {
        private const val TAG = "NeuroPad/Adb"
        private const val DAY_MS = 86_400_000L
        const val LOOPBACK = "127.0.0.1"

        @Volatile
        private var instance: AdbConnectionManager? = null

        fun get(context: Context): AdbConnectionManager =
            instance ?: synchronized(this) {
                instance ?: run {
                    installTlsProvider()
                    AdbConnectionManager(context.applicationContext).also { instance = it }
                }
            }

        /** Wireless debugging needs TLS 1.3, which the platform only ships from Android 10. */
        private fun installTlsProvider() {
            try {
                if (Security.getProvider("Conscrypt") == null) {
                    Security.insertProviderAt(Conscrypt.newProvider(), 1)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Conscrypt not available", t)
            }
        }
    }
}
