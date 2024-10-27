package io.homeassistant.companion.android.common.util.feature

import io.homeassistant.companion.android.common.data.LocalStorage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private class FakeLocalStorage : LocalStorage {
    val internalStorage = hashMapOf<String, Any?>()

    override suspend fun putString(key: String, value: String?) {
        internalStorage.put(key, value)
    }

    override suspend fun getString(key: String): String? {
        return internalStorage.getOrDefault(key, null) as String?
    }

    override suspend fun putLong(key: String, value: Long?) {
        internalStorage.put(key, value)
    }

    override suspend fun getLong(key: String): Long? {
        return internalStorage.getOrDefault(key, null) as Long?
    }

    override suspend fun putInt(key: String, value: Int?) {
        internalStorage.put(key, value)
    }

    override suspend fun getInt(key: String): Int? {
        return internalStorage.getOrDefault(key, null) as Int?
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        internalStorage.put(key, value)
    }

    override suspend fun getBoolean(key: String): Boolean {
        throw NotImplementedError("Not implemented for the test - should not be needed")
    }

    override suspend fun getBooleanOrNull(key: String): Boolean? {
        return internalStorage.getOrDefault(key, null) as Boolean?
    }

    override suspend fun putStringSet(key: String, value: Set<String>) {
        NOT_NEEDED()
    }

    override suspend fun getStringSet(key: String): Set<String>? {
        NOT_NEEDED()
    }

    override suspend fun remove(key: String) {
        NOT_NEEDED()
    }
}

class LocalFeatureValueTest {

    // DefaultFeatureValue
    @Test
    fun `Given a feature definition to DefaultFeatureValue when retrieving its value, then default value should be returned`() = runTest {
        // Given
        val featureValue = DefaultFeatureValue(TestBooleanFeature)

        // When
        val value = featureValue.getValue()

        // Then
        assertEquals(TestBooleanFeature.defaultValue, value)
    }

    // LocalFeatureValue
    @Test
    fun `Given a string feature definition to LocalFeatureValue when retrieving its value with no value in local storage, then default value should be returned`() = runTest {
        // Given
        val localStorage = FakeLocalStorage()
        val featureValue = LocalFeatureValue(localStorage, TestStringFeature)

        // When
        val value = featureValue.getValue()

        // Then
        assertEquals(TestStringFeature.defaultValue, value)
    }

    @Test
    fun `Given a boolean feature definition to LocalFeatureValue when retrieving its value with no value in local storage, then default value should be returned`() = runTest {
        // Given
        val localStorage = FakeLocalStorage()
        val featureValue = LocalFeatureValue(localStorage, TestBooleanFeature)

        // When
        val value = featureValue.getValue()

        // Then
        assertEquals(TestBooleanFeature.defaultValue, value)
    }

    @Test
    fun `Given a string feature definition to LocalFeatureValue when retrieving its value with a value in local storage, then the value from local storage should be returned`() = runTest {
        // Given
        val localStorage = FakeLocalStorage()
        val featureValue = LocalFeatureValue(localStorage, TestStringFeature)
        val expectedValue = "helloWorld"
        localStorage.internalStorage.put(TestStringFeature::class.java.canonicalName!!, expectedValue)

        // When
        val value = featureValue.getValue()

        // Then
        assertEquals(expectedValue, value)
    }

    @Test
    fun `Given a boolean feature definition to LocalFeatureValue when retrieving its value with a value in local storage, then the value from local storage should be returned`() = runTest {
        // Given
        val localStorage = FakeLocalStorage()
        val featureValue = LocalFeatureValue(localStorage, TestBooleanFeature)
        val expectedValue = !TestBooleanFeature.defaultValue
        localStorage.internalStorage.put(TestBooleanFeature::class.java.canonicalName!!, expectedValue)

        // When
        val value = featureValue.getValue()

        // Then
        assertEquals(expectedValue, value)
    }

    @Test
    fun `Given an anonymous feature definition when accessing the value within LocalFeatureValue, then throw IllegalStateException`() = runTest {
        // Given
        val localStorage = FakeLocalStorage()
        val expectedFeatureName = "jarvis"
        val feature = object : FeatureDefinition.BooleanFeatureDefinition {
            override val featureName: String = expectedFeatureName
            override val defaultValue: Boolean
                get() = NOT_NEEDED()
        }

        // When and Then
        assertThrows<IllegalStateException>("The class for the feature ($expectedFeatureName) should be non-anonymous nor a lambda otherwise") {
            LocalFeatureValue(localStorage, feature)
        }
    }
}
