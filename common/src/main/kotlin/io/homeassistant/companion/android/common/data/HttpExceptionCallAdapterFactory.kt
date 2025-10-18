package io.homeassistant.companion.android.common.data

import io.homeassistant.companion.android.common.exception.HttpException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

class HttpExceptionCallAdapterFactory : CallAdapter.Factory() {
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
        delegate.enqueue(object : Callback<R> {
            override fun onResponse(call: Call<R>, response: Response<R>) {
                callback.onResponse(call, response)
            }

            override fun onFailure(call: Call<R>, t: Throwable) {
                val convertedException = if (t is retrofit2.HttpException) {
                    HttpException(
                        code = t.code(),
                        message = t.response()?.errorBody()?.string() ?: t.message(),
                    )
                } else {
                    t
                }
                callback.onFailure(call, convertedException)
            }
        })
    }

    override fun clone(): Call<R> = HttpExceptionCall(delegate.clone())
}
