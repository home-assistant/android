package io.homeassistant.companion.android.common.data.network

import android.net.ConnectivityManager
import android.net.Network
import android.os.SystemClock
import io.homeassistant.companion.android.util.sensitive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.ParseException
import kotlin.random.Random
import timber.log.Timber

private const val TAG = "NetworkBoundDnsLookup"
private const val DNS_PORT = 53
private const val DNS_QUERY_TIMEOUT_MS = 5_000
private const val MAX_SPURIOUS_RESPONSES = 8
private const val DNS_TYPE_A = 1
private const val DNS_TYPE_AAAA = 28
private const val DNS_CLASS_IN = 1
private const val DNS_OPCODE_QUERY = 0
private const val DNS_RCODE_NO_ERROR = 0
private const val DNS_TRANSACTION_ID_RANGE = 0x10000
private const val MAX_DNS_LABEL_LENGTH = 63
private const val MAX_DNS_NAME_LENGTH = 255

/**
 * A DNS query packet and its transaction identifier.
 */
internal data class DnsQuery(val packet: ByteArray, val transactionId: Int)

/**
 * Result of parsing a single DNS UDP response packet.
 */
internal sealed interface DnsResponseParseResult {
    /** Packet is unrelated or malformed and should be ignored while waiting for more responses. */
    data object Ignored : DnsResponseParseResult

    /** Packet matches the query; [addresses] may be empty when the name exists but has no records. */
    data class Matched(val addresses: List<InetAddress>) : DnsResponseParseResult
}

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
    fun lookup(network: Network, connectivityManager: ConnectivityManager, hostname: String): List<InetAddress> {
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
        val aAddresses = queryRecordType(
            network = network,
            dnsServers = dnsServers,
            hostname = hostname,
            recordType = DNS_TYPE_A,
        )
        val combinedAddresses = (aaaaAddresses + aAddresses).distinct()
        if (combinedAddresses.isNotEmpty()) {
            return combinedAddresses
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
                    sensitive(hostname),
                    recordType,
                    sensitive(dnsServer.hostAddress ?: dnsServer.toString()),
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
                    query.packet,
                    query.packet.size,
                    dnsServer,
                    DNS_PORT,
                ),
            )

            val deadlineMs = SystemClock.elapsedRealtime() + DNS_QUERY_TIMEOUT_MS
            var spuriousResponses = 0
            while (SystemClock.elapsedRealtime() < deadlineMs && spuriousResponses < MAX_SPURIOUS_RESPONSES) {
                val remainingMs = (deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(1)
                socket.soTimeout = remainingMs.toInt()
                val responseBuffer = ByteArray(MAX_DNS_PACKET_SIZE)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                try {
                    socket.receive(responsePacket)
                } catch (_: SocketTimeoutException) {
                    break
                }

                if (!isResponseFromDnsServer(responsePacket, dnsServer)) {
                    spuriousResponses++
                    continue
                }

                when (
                    val result = parseDnsResponse(
                        response = responsePacket.data.copyOf(responsePacket.length),
                        recordType = recordType,
                        expectedTransactionId = query.transactionId,
                    )
                ) {
                    DnsResponseParseResult.Ignored -> {
                        spuriousResponses++
                    }

                    is DnsResponseParseResult.Matched -> {
                        return result.addresses
                    }
                }
            }
            return emptyList()
        }
    }

    private fun isResponseFromDnsServer(responsePacket: DatagramPacket, dnsServer: InetAddress): Boolean {
        return responsePacket.port == DNS_PORT && responsePacket.address == dnsServer
    }

    /**
     * Builds a standard DNS query packet for [hostname] and [recordType].
     */
    internal fun buildDnsQuery(hostname: String, recordType: Int): DnsQuery {
        val question = encodeHostnameQuestion(hostname, recordType)
        val packet = ByteArray(DNS_HEADER_SIZE + question.size)
        val transactionId = Random.nextInt(from = 0, until = DNS_TRANSACTION_ID_RANGE)
        packet[0] = (transactionId shr 8).toByte()
        packet[1] = transactionId.toByte()
        packet[2] = ((DNS_OPCODE_QUERY shl 3) or 0x01).toByte()
        packet[3] = 0
        packet[4] = 0
        packet[5] = 1
        System.arraycopy(question, 0, packet, DNS_HEADER_SIZE, question.size)
        return DnsQuery(packet = packet, transactionId = transactionId)
    }

    /**
     * Parses A or AAAA answers from a DNS response packet.
     */
    internal fun parseDnsResponse(
        response: ByteArray,
        recordType: Int,
        expectedTransactionId: Int,
    ): DnsResponseParseResult {
        if (response.size < DNS_HEADER_SIZE) {
            return DnsResponseParseResult.Ignored
        }
        val responseTransactionId = readUInt16(response, offset = 0)
        if (responseTransactionId != expectedTransactionId) {
            return DnsResponseParseResult.Ignored
        }
        val rcode = response[3].toInt() and 0x0F
        if (rcode != DNS_RCODE_NO_ERROR) {
            return DnsResponseParseResult.Matched(emptyList())
        }

        val questionEnd = skipDnsName(response, DNS_HEADER_SIZE)
        if (questionEnd + 4 > response.size) {
            return DnsResponseParseResult.Ignored
        }

        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        val authorityCount = ((response[8].toInt() and 0xFF) shl 8) or (response[9].toInt() and 0xFF)
        val additionalCount = ((response[10].toInt() and 0xFF) shl 8) or (response[11].toInt() and 0xFF)

        var offset = questionEnd + 4
        val addresses = mutableListOf<InetAddress>()
        offset = collectDnsRecords(
            response = response,
            startOffset = offset,
            recordCount = answerCount,
            recordType = recordType,
            addresses = addresses,
        )
        if (addresses.isNotEmpty()) {
            return DnsResponseParseResult.Matched(addresses)
        }

        offset = skipDnsRecords(response, startOffset = offset, recordCount = authorityCount)
        collectDnsRecords(
            response = response,
            startOffset = offset,
            recordCount = additionalCount,
            recordType = recordType,
            addresses = addresses,
        )
        return DnsResponseParseResult.Matched(addresses)
    }

    private fun collectDnsRecords(
        response: ByteArray,
        startOffset: Int,
        recordCount: Int,
        recordType: Int,
        addresses: MutableList<InetAddress>,
    ): Int {
        var offset = startOffset
        repeat(recordCount) {
            if (offset >= response.size) {
                return offset
            }
            offset = skipDnsName(response, offset)
            if (offset + 10 > response.size) {
                return offset
            }
            val type = readUInt16(response, offset)
            offset += 2
            offset += 2 // class
            offset += 4 // ttl
            val dataLength = readUInt16(response, offset)
            offset += 2
            if (offset + dataLength > response.size) {
                return offset
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
        return offset
    }

    private fun skipDnsRecords(response: ByteArray, startOffset: Int, recordCount: Int): Int {
        var offset = startOffset
        repeat(recordCount) {
            if (offset >= response.size) {
                return offset
            }
            offset = skipDnsName(response, offset)
            if (offset + 10 > response.size) {
                return offset
            }
            offset += 8 // type, class, ttl
            val dataLength = readUInt16(response, offset)
            offset += 2 + dataLength
        }
        return offset
    }

    private fun encodeHostnameQuestion(hostname: String, recordType: Int): ByteArray {
        val asciiHostname = try {
            IDN.toASCII(hostname, IDN.ALLOW_UNASSIGNED)
        } catch (exception: ParseException) {
            throw UnknownHostException(hostname).apply { initCause(exception) }
        } catch (exception: IllegalArgumentException) {
            throw UnknownHostException(hostname).apply { initCause(exception) }
        }
        val labels = asciiHostname.split('.').filter { it.isNotEmpty() }
        var encodedNameLength = 0
        for (label in labels) {
            if (label.length > MAX_DNS_LABEL_LENGTH) {
                throw UnknownHostException(hostname)
            }
            encodedNameLength += label.length + 1
        }
        encodedNameLength += 1
        if (encodedNameLength > MAX_DNS_NAME_LENGTH) {
            throw UnknownHostException(hostname)
        }

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
