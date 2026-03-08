package sh.haven.feature.connections

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "NetworkDiscovery"

data class DiscoveredHost(
    val address: String,
    val hostname: String?,
    val port: Int = 22,
    val source: String, // "mDNS", "scan", or "localhost"
)

data class LocalVmStatus(
    val terminalAppInstalled: Boolean = false,
    val sshPort: Int? = null,
    val vncPort: Int? = null,
)

/**
 * Discovers SSH hosts on the local network via:
 * 1. mDNS/DNS-SD: listens for _ssh._tcp services (Avahi/Bonjour)
 * 2. ARP scan: reads /proc/net/arp and probes port 22
 */
class NetworkDiscovery(private val context: Context) {

    private val _hosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val hosts: StateFlow<List<DiscoveredHost>> = _hosts.asStateFlow()

    private val _localVm = MutableStateFlow(LocalVmStatus())
    val localVm: StateFlow<LocalVmStatus> = _localVm.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val mdnsHosts = mutableSetOf<DiscoveredHost>()
    private val arpHosts = mutableSetOf<DiscoveredHost>()

    fun start() {
        startMdns()
    }

    fun stop() {
        stopMdns()
        mdnsHosts.clear()
        arpHosts.clear()
        _hosts.value = emptyList()
        _localVm.value = LocalVmStatus()
    }

    /**
     * Probe localhost for SSH/VNC ports commonly used by the Android Linux VM.
     * The Terminal app auto-forwards guest TCP ports to localhost via vsock.
     */
    suspend fun scanLocalVm() {
        withContext(Dispatchers.IO) {
            val terminalInstalled = isTerminalAppInstalled()
            val sshPorts = listOf(8022, 2222, 22)
            val vncPorts = listOf(5900, 5901, 5902)
            val sshPort = sshPorts.firstOrNull { probePort("127.0.0.1", it, 200) }
            val vncPort = vncPorts.firstOrNull { probePort("127.0.0.1", it, 200) }
            _localVm.value = LocalVmStatus(
                terminalAppInstalled = terminalInstalled,
                sshPort = sshPort,
                vncPort = vncPort,
            )
            if (terminalInstalled) {
                Log.d(TAG, "Android Terminal app detected")
            }
            if (sshPort != null) {
                Log.d(TAG, "Local VM SSH detected on port $sshPort")
            }
            if (vncPort != null) {
                Log.d(TAG, "Local VM VNC detected on port $vncPort")
            }
        }
    }

    private fun isTerminalAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                "com.android.virtualization.terminal", 0,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scan the local /24 subnet for hosts with SSH on port 22.
     * Uses ConnectivityManager to determine the local IP, then probes
     * all 254 addresses in parallel with a short timeout.
     */
    suspend fun scanSubnet() {
        withContext(Dispatchers.IO) {
            try {
                val baseIp = getLocalSubnetBase() ?: run {
                    Log.d(TAG, "Could not determine local subnet")
                    return@withContext
                }
                Log.d(TAG, "Scanning subnet $baseIp.1-254 for SSH")

                coroutineScope {
                    val jobs = (1..254).map { i ->
                        async(Dispatchers.IO) {
                            val ip = "$baseIp.$i"
                            if (probePort(ip, 22, timeoutMs = 400)) {
                                val hostname = resolveHostname(ip)
                                val host = DiscoveredHost(
                                    address = ip,
                                    hostname = hostname,
                                    port = 22,
                                    source = "scan",
                                )
                                synchronized(arpHosts) {
                                    arpHosts.add(host)
                                }
                                mergeAndEmit()
                                Log.d(TAG, "SSH found: $ip ($hostname)")
                            }
                        }
                    }
                    jobs.awaitAll()
                }
                Log.d(TAG, "Subnet scan complete: ${arpHosts.size} SSH hosts found")
            } catch (e: Exception) {
                Log.e(TAG, "Subnet scan failed", e)
            }
        }
    }

    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE declared in app manifest
    private fun getLocalSubnetBase(): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val props: LinkProperties = cm.getLinkProperties(network) ?: return null
            val ipv4 = props.linkAddresses
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?: return null
            val parts = ipv4.hostAddress?.split(".") ?: return null
            if (parts.size != 4) return null
            return "${parts[0]}.${parts[1]}.${parts[2]}"
        } catch (e: Exception) {
            Log.e(TAG, "getLocalSubnetBase failed", e)
            return null
        }
    }

    private fun startMdns() {
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "mDNS discovery started for $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "mDNS found: ${serviceInfo.serviceName}")
                    mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.d(TAG, "mDNS resolve failed: ${info.serviceName} error=$errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val addr = info.host?.hostAddress ?: return
                            val host = DiscoveredHost(
                                address = addr,
                                hostname = info.serviceName,
                                port = info.port,
                                source = "mDNS",
                            )
                            Log.d(TAG, "mDNS resolved: $addr (${info.serviceName}:${info.port})")
                            mdnsHosts.add(host)
                            mergeAndEmit()
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    mdnsHosts.removeAll { it.hostname == serviceInfo.serviceName }
                    mergeAndEmit()
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS discovery stopped")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS start failed: error=$errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS stop failed: error=$errorCode")
                }
            }

            discoveryListener = listener
            mgr.discoverServices("_ssh._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "mDNS init failed", e)
        }
    }

    private fun stopMdns() {
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
            // Already stopped
        }
        discoveryListener = null
        nsdManager = null
    }

    private fun mergeAndEmit() {
        val all = (mdnsHosts + arpHosts)
            .distinctBy { it.address }
            .sortedWith(compareBy(
                { it.port != 22 },       // port 22 first
                { it.source != "mDNS" }, // mDNS before ARP
                { it.hostname == null },  // named hosts before bare IPs
                { it.address },
            ))
        _hosts.value = all
    }

    private fun probePort(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            val addr = java.net.InetAddress.getByName(ip)
            val name = addr.canonicalHostName
            if (name != ip) name else null
        } catch (_: Exception) {
            null
        }
    }
}
