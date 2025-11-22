package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.exception.HttpException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

/**
 * A factory converting Retrofit-specific HttpException into domain-defined [HttpException].
 * Prevents leaking Retrofit types into the domain layer.
 */
internal class HttpExceptionCallAdapterFactory : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }

        val callType = getParameterUpperBound(0, returnType as ParameterizedType)
        return HttpExceptionCallAdapter<Any>(callType)
    }
}

private class HttpExceptionCallAdapter<R>(private val responseType: Type) : CallAdapter<R, Call<R>> {

    override fun responseType(): Type = responseType

    override fun adapt(call: Call<R>): Call<R> = HttpExceptionCall(call)
}

private class HttpExceptionCall<R>(private val delegate: Call<R>) : Call<R> by delegate {

    override fun enqueue(callback: Callback<R>) {
        delegate.enqueue(
            object : Callback<R> {
                override fun onResponse(call: Call<R>, response: Response<R>) {
                    if (response.isSuccessful) {
                        callback.onResponse(call, response)
                    } else {
                        callback.onFailure(
                            call,
                            HttpException(
                                code = response.code(),
                                message = response.errorBody()?.string() ?: response.message(),
                            ),
                        )
                    }
                }

                override fun onFailure(call: Call<R>, t: Throwable) {
                    callback.onFailure(call, t)
                }
            },
        )
    }

    override fun clone(): Call<R> = HttpExceptionCall(delegate.clone())
}
