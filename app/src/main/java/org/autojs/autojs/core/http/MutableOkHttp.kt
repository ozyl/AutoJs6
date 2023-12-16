package org.autojs.autojs.core.http

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Created by Stardust on Apr 11, 2018.
 * Modified by SuperMonster003 as of Sep 8, 2022.
 */
class MutableOkHttp : OkHttpClient() {

    private var mOkHttpClient: OkHttpClient
    private val maxRetries = 3
    private var mTimeout = 30 * 1000L
    private var mTimeoutInterceptor = Interceptor { chain: Interceptor.Chain ->

        val request = chain.request()
        val timeout = request.header("autojs6Timeout")?.toIntOrNull()?:0
        if (timeout==0){
            chain.proceed(request)
        }else{
            chain.withConnectTimeout(timeout, TimeUnit.MILLISECONDS)
                .withReadTimeout(timeout, TimeUnit.MILLISECONDS)
                .withWriteTimeout(timeout, TimeUnit.MILLISECONDS)
                .proceed(request)
        }
    }
    private val mRetryInterceptor = Interceptor { chain: Interceptor.Chain ->
        val request = chain.request()
        var response: Response? = null
        var tryCount = 0
        do {
            var isSuccessful: Boolean
            try {
                response?.close()
                response = chain.proceed(request).also { isSuccessful = it.isSuccessful }
            } catch (e: Exception) {
                isSuccessful = false
                if (tryCount >= maxRetries) {
                    throw e
                }
            }
            if (isSuccessful || tryCount >= maxRetries) {
                break
            }
            tryCount++
        } while (true)
        response ?: throw Exception("Failed to make a request")
    }

    init {
        mOkHttpClient = newClient(Builder())
    }

    fun client(): OkHttpClient {
        return mOkHttpClient
    }

    private fun newClient(builder: Builder): OkHttpClient {
        builder.readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
        for (interceptor in listOf(mRetryInterceptor,mTimeoutInterceptor)) {
            builder.addInterceptor(interceptor)
        }
        return builder.build()
    }

    var timeout: Long
        get() = mTimeout
        set(timeout) {
            if (timeout==mTimeout){
                return
            }
            mTimeout = timeout
            muteClient()
        }

    @Synchronized
    fun muteClient(builder: Builder) {
        mOkHttpClient = newClient(builder)
    }

    @Synchronized
    private fun muteClient() = muteClient(mOkHttpClient.newBuilder().apply {
        interceptors().clear()
    })

}