package io.homeassistant.companion.android.common.data.keychain

import android.content.Context
import android.security.KeyChain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KeyChainRepositoryImplTest {

    private val prefsRepository = mockk<PrefsRepository>(relaxUnitFun = true)
    private val context = mockk<Context>()
    private val repository = KeyChainRepositoryImpl(context, prefsRepository)

    @BeforeEach
    fun setUp() {
        mockkStatic(KeyChain::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(KeyChain::class)
    }

    private fun givenKeyChainCert(alias: String): Pair<PrivateKey, Array<X509Certificate>> {
        val key = mockk<PrivateKey>()
        val chain = arrayOf(mockk<X509Certificate>())
        every { KeyChain.getCertificateChain(context, alias) } returns chain
        every { KeyChain.getPrivateKey(context, alias) } returns key
        return key to chain
    }

    @Test
    fun `Given a persisted alias when loading then the certificate is available on the returned provider`() = runTest {
        coEvery { prefsRepository.getKeyAlias() } returns "alias"
        val (key, chain) = givenKeyChainCert("alias")

        val provider = repository.getClientCertProvider()

        assertEquals(key, provider.certificate?.privateKey)
        assertEquals(chain, provider.certificate?.chain)
    }

    @Test
    fun `Given no persisted alias when loading then the provider has no certificate`() = runTest {
        coEvery { prefsRepository.getKeyAlias() } returns ""

        val provider = repository.getClientCertProvider()

        assertNull(provider.certificate)
    }

    @Test
    fun `Given a loaded certificate when selecting another alias then the new certificate replaces the old one`() = runTest {
        coEvery { prefsRepository.getKeyAlias() } returns "old"
        givenKeyChainCert("old")
        val provider = repository.getClientCertProvider()
        val (newKey, _) = givenKeyChainCert("new")

        val selected = repository.select("new")

        assertEquals(newKey, selected?.privateKey)
        assertEquals(newKey, provider.certificate?.privateKey)
        coVerify { prefsRepository.saveKeyAlias("new") }
    }

    @Test
    fun `Given a loaded certificate when clearing then the provider has no certificate and the alias is forgotten`() = runTest {
        coEvery { prefsRepository.getKeyAlias() } returns "alias"
        givenKeyChainCert("alias")
        val provider = repository.getClientCertProvider()

        repository.clear()

        assertNull(provider.certificate)
        coVerify { prefsRepository.saveKeyAlias("") }
    }

    @Test
    fun `Given the KeyChain throws when loading then the provider has no certificate`() = runTest {
        coEvery { prefsRepository.getKeyAlias() } returns "alias"
        every { KeyChain.getCertificateChain(context, "alias") } throws IllegalStateException("KeyChain unavailable")
        every { KeyChain.getPrivateKey(context, "alias") } throws IllegalStateException("KeyChain unavailable")

        val provider = repository.getClientCertProvider()

        assertNull(provider.certificate)
    }
}
