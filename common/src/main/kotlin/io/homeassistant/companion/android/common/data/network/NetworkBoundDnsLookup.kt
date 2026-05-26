package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.random.Random
import timber.log.Timber

private const val TAG = "NetworkBoundDnsLookup"
private const val DNS_PORT = 53
private const val DNS_QUERY_TIMEOUT_MS = 5_000
private const val DNS_TYPE_A = 1
private const val DNS_TYPE_AAAA = 28
private const val DNS_CLASS_IN = 1
private const val DNS_OPCODE_QUERY = 0
private const val DNS_RCODE_NO_ERROR = 0

/**
 * Issues explicit AAAA and A DNS queries on [network] for API levels below 29 where
 * [android.net.DnsResolver] is unavailable.
 *
 * Uses the DNS servers from [ConnectivityManager.getLinkProperties] so lookups follow the active
 * network instead of skipping AAAA records when no `2000::` route is present.
 */
internal object NetworkBoundDnsLookup {

    /**
     * Resolves [hostname] on [network], preferring AAAA records when present.
     */
    fun lookup(
        network: Network,
        connectivityManager: ConnectivityManager,
        hostname: String,
    ): List<InetAddress> {
        val dnsServers = connectivityManager.getLinkProperties(network)?.dnsServers.orEmpty()
        if (dnsServers.isEmpty()) {
            return network.getAllByName(hostname).toList()
        }

        val aaaaAddresses = queryRecordType(
            network = network,
            dnsServers = dnsServers,
            hostname = hostname,
            recordType = DNS_TYPE_AAAA,
        )
        if (aaaaAddresses.isNotEmpty()) {
            return aaaaAddresses.distinct()
        }

        val aAddresses = queryRecordType(
            network = network,
            dnsServers = dnsServers,
            hostname = hostname,
            recordType = DNS_TYPE_A,
        )
        if (aAddresses.isNotEmpty()) {
            return aAddresses.distinct()
        }

        return try {
            network.getAllByName(hostname).toList()
        } catch (exception: UnknownHostException) {
            throw UnknownHostException(hostname)
        }
    }

    private fun queryRecordType(
        network: Network,
        dnsServers: List<InetAddress>,
        hostname: String,
        recordType: Int,
    ): List<InetAddress> {
        for (dnsServer in dnsServers) {
            try {
                val addresses = queryDnsServer(
                    network = network,
                    dnsServer = dnsServer,
                    hostname = hostname,
                    recordType = recordType,
                )
                if (addresses.isNotEmpty()) {
                    return addresses
                }
            } catch (exception: Exception) {
                Timber.tag(TAG).d(
                    exception,
                    "DNS query failed for %s (type=%d) via %s",
                    hostname,
                    recordType,
                    dnsServer.hostAddress,
                )
            }
        }
        return emptyList()
    }

    private fun queryDnsServer(
        network: Network,
        dnsServer: InetAddress,
        hostname: String,
        recordType: Int,
    ): List<InetAddress> {
        val query = buildDnsQuery(hostname = hostname, recordType = recordType)
        DatagramSocket().use { socket ->
            socket.soTimeout = DNS_QUERY_TIMEOUT_MS
            network.bindSocket(socket)
            socket.send(
                DatagramPacket(
                    query,
                    query.size,
                    dnsServer,
                    DNS_PORT,
                ),
            )
            val responseBuffer = ByteArray(MAX_DNS_PACKET_SIZE)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            return parseDnsResponse(
                response = responsePacket.data.copyOf(responsePacket.length),
                recordType = recordType,
            )
        }
    }

    /**
     * Builds a standard DNS query packet for [hostname] and [recordType].
     */
    internal fun buildDnsQuery(hostname: String, recordType: Int): ByteArray {
        val question = encodeHostnameQuestion(hostname, recordType)
        val packet = ByteArray(DNS_HEADER_SIZE + question.size)
        val transactionId = Random.nextInt(from = 0, until = Short.MAX_VALUE.toInt())
        packet[0] = (transactionId shr 8).toByte()
        packet[1] = transactionId.toByte()
        packet[2] = ((DNS_OPCODE_QUERY shl 3) or 0x01).toByte()
        packet[3] = 0
        packet[4] = 0
        packet[5] = 1
        System.arraycopy(question, 0, packet, DNS_HEADER_SIZE, question.size)
        return packet
    }

    /**
     * Parses A or AAAA answers from a DNS response packet.
     */
    internal fun parseDnsResponse(response: ByteArray, recordType: Int): List<InetAddress> {
        if (response.size < DNS_HEADER_SIZE) {
            return emptyList()
        }
        val rcode = response[3].toInt() and 0x0F
        if (rcode != DNS_RCODE_NO_ERROR) {
            return emptyList()
        }

        val questionEnd = skipDnsName(response, DNS_HEADER_SIZE)
        if (questionEnd + 4 > response.size) {
            return emptyList()
        }
        var offset = questionEnd + 4

        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        val addresses = mutableListOf<InetAddress>()
        repeat(answerCount) {
            if (offset >= response.size) {
                return@repeat
            }
            offset = skipDnsName(response, offset)
            if (offset + 10 > response.size) {
                return@repeat
            }
            val type = readUInt16(response, offset)
            offset += 2
            offset += 2 // class
            offset += 4 // ttl
            val dataLength = readUInt16(response, offset)
            offset += 2
            if (offset + dataLength > response.size) {
                return@repeat
            }
            if (type == recordType) {
                when (recordType) {
                    DNS_TYPE_A -> {
                        if (dataLength == 4) {
                            addresses += InetAddress.getByAddress(
                                response.copyOfRange(offset, offset + dataLength),
                            )
                        }
                    }
                    DNS_TYPE_AAAA -> {
                        if (dataLength == 16) {
                            addresses += InetAddress.getByAddress(
                                response.copyOfRange(offset, offset + dataLength),
                            )
                        }
                    }
                }
            }
            offset += dataLength
        }
        return addresses
    }

    private fun encodeHostnameQuestion(hostname: String, recordType: Int): ByteArray {
        val labels = hostname.split('.').filter { it.isNotEmpty() }
        val question = ByteArray(
            labels.sumOf { it.length + 1 } + 1 + 2 + 2,
        )
        var offset = 0
        for (label in labels) {
            question[offset++] = label.length.toByte()
            label.toByteArray(Charsets.US_ASCII).copyInto(question, offset)
            offset += label.length
        }
        question[offset++] = 0
        question[offset++] = (recordType shr 8).toByte()
        question[offset++] = recordType.toByte()
        question[offset++] = (DNS_CLASS_IN shr 8).toByte()
        question[offset] = DNS_CLASS_IN.toByte()
        return question
    }

    private fun skipDnsName(response: ByteArray, startOffset: Int): Int {
        var offset = startOffset
        while (offset < response.size) {
            val length = response[offset].toInt() and 0xFF
            if (length == 0) {
                return offset + 1
            }
            if (length and 0xC0 == 0xC0) {
                return offset + 2
            }
            offset += length + 1
        }
        return offset
    }

    private fun readUInt16(response: ByteArray, offset: Int): Int {
        return ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
    }
}

private const val DNS_HEADER_SIZE = 12
private const val MAX_DNS_PACKET_SIZE = 512
