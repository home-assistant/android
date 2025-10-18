package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.exception.HttpException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.ParameterizedType

class HttpExceptionCallAdapterTest {

    private val retrofit = mockk<Retrofit>()
    private val factory = HttpExceptionCallAdapterFactory()

    @Test
    fun `Given a Call return type when getting adapter then it returns non-null adapter with correct response type`() {
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        val adapter = factory.get(returnType, emptyArray(), retrofit)

        assertNotNull(adapter)
        assertEquals(String::class.java, adapter!!.responseType())
    }

    @Test
    fun `Given a non-Call return type when getting adapter then it returns null`() {
        val returnType = mockk<ParameterizedType> {
            every { this@mockk.toString() } returns "SomeOtherType"
            every { this@mockk.rawType } returns String::class.java
        }
        every { retrofit.callFactory() } returns mockk()

        val adapter = factory.get(returnType, emptyArray(), retrofit)

        assertNull(adapter)
    }

    @Test
    fun `Given a Call return type with Int when getting adapter then it returns adapter with Int response type`() {
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(Int::class.java)
        }

        val adapter = factory.get(returnType, emptyArray(), retrofit)

        assertEquals(Int::class.java, adapter!!.responseType())
    }

    @Test
    fun `Given a retrofit HttpException when call fails then it converts to custom HttpException with correct code and message`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val callbackSlot = slot<Callback<String>>()
        every { delegateCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(
                delegateCall,
                retrofit2.HttpException(
                    Response.error<String>(
                        404,
                        "Not Found".toResponseBody(),
                    ),
                ),
            )
        }

        val resultCallback = mockk<Callback<String>>(relaxed = true)
        httpExceptionCall.enqueue(resultCallback)

        val exceptionSlot = slot<Throwable>()
        verify { resultCallback.onFailure(any<Call<String>>(), capture(exceptionSlot)) }

        assertInstanceOf(HttpException::class.java, exceptionSlot.captured)
        val httpException = exceptionSlot.captured as HttpException
        assertEquals(404, httpException.code)
        assertEquals("Not Found", httpException.message)
    }

    @Test
    fun `Given a non-HTTP exception when call fails then it passes through exception unchanged`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val ioException = IOException("Network error")
        val callbackSlot = slot<Callback<String>>()
        every { delegateCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(delegateCall, ioException)
        }

        val resultCallback = mockk<Callback<String>>(relaxed = true)
        httpExceptionCall.enqueue(resultCallback)

        val exceptionSlot = slot<Throwable>()
        verify { resultCallback.onFailure(any<Call<String>>(), capture(exceptionSlot)) }

        assertEquals(ioException, exceptionSlot.captured)
    }

    @Test
    fun `Given a successful response when call succeeds then it passes through response unchanged`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val successResponse = Response.success("Success")
        val callbackSlot = slot<Callback<String>>()
        every { delegateCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onResponse(delegateCall, successResponse)
        }

        val resultCallback = mockk<Callback<String>>(relaxed = true)
        httpExceptionCall.enqueue(resultCallback)

        val responseSlot = slot<Response<String>>()
        verify { resultCallback.onResponse(any<Call<String>>(), capture(responseSlot)) }

        assertEquals(successResponse, responseSlot.captured)
    }

    @Test
    fun `Given an HttpExceptionCall when cloning then it creates a new instance`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val clonedDelegateCall = mockk<Call<String>>(relaxed = true)
        every { delegateCall.clone() } returns clonedDelegateCall

        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val clonedCall = httpExceptionCall.clone()

        verify { delegateCall.clone() }
        assertNotNull(clonedCall)
    }

    @Test
    fun `Given an HttpExceptionCall when requesting then it delegates to underlying call`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val expectedRequest = Request.Builder().url("http://example.com").build()
        every { delegateCall.request() } returns expectedRequest

        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val actualRequest = httpExceptionCall.request()

        assertEquals(expectedRequest, actualRequest)
        verify { delegateCall.request() }
    }

    @Test
    fun `Given retrofit HttpException with empty error body when call fails then it falls back to default message`() {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val callbackSlot = slot<Callback<String>>()
        every { delegateCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(
                delegateCall,
                retrofit2.HttpException(
                    Response.error<String>(
                        500,
                        "".toResponseBody(),
                    ),
                ),
            )
        }

        val resultCallback = mockk<Callback<String>>(relaxed = true)
        httpExceptionCall.enqueue(resultCallback)

        val exceptionSlot = slot<Throwable>()
        verify { resultCallback.onFailure(any<Call<String>>(), capture(exceptionSlot)) }

        assertInstanceOf(HttpException::class.java, exceptionSlot.captured)
        val httpException = exceptionSlot.captured as HttpException
        assertEquals(500, httpException.code)
        assertEquals("", httpException.message)
    }

    @ParameterizedTest
    @CsvSource(
        "400, Bad Request",
        "401, Unauthorized",
        "403, Forbidden",
        "500, Internal Server Error",
        "503, Service Unavailable",
    )
    fun `Given retrofit HttpException with status code when call fails then it converts to custom HttpException with correct code and message`(
        statusCode: Int,
        errorMessage: String,
    ) {
        val delegateCall = mockk<Call<String>>(relaxed = true)
        val returnType = mockk<ParameterizedType> {
            every { rawType } returns Call::class.java
            every { actualTypeArguments } returns arrayOf(String::class.java)
        }

        @Suppress("UNCHECKED_CAST")
        val adapter = factory.get(returnType, emptyArray(), retrofit) as retrofit2.CallAdapter<String, Call<String>>
        val httpExceptionCall = adapter.adapt(delegateCall)

        val callbackSlot = slot<Callback<String>>()
        every { delegateCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(
                delegateCall,
                retrofit2.HttpException(
                    Response.error<String>(
                        statusCode,
                        errorMessage.toResponseBody(),
                    ),
                ),
            )
        }

        val resultCallback = mockk<Callback<String>>(relaxed = true)
        httpExceptionCall.enqueue(resultCallback)

        val exceptionSlot = slot<Throwable>()
        verify { resultCallback.onFailure(any<Call<String>>(), capture(exceptionSlot)) }

        assertInstanceOf(HttpException::class.java, exceptionSlot.captured)
        val httpException = exceptionSlot.captured as HttpException
        assertEquals(statusCode, httpException.code)
        assertEquals(errorMessage, httpException.message)
    }
}
