package pl.igorwumk.drivetracker.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.20:8000/"

    object TrafficStats {
        @Volatile var bytesSent: Long = 0
        @Volatile var bytesReceived: Long = 0

        fun reset() {
            bytesSent = 0
            bytesReceived = 0
        }
    }

    // To force correct Content-Type
    val contentTypeInterceptor = Interceptor { chain ->
        val originalRequest: Request = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(newRequest)
    }

    // To update traffic stats
    class TrafficInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // count request body size
            val toSend = request.body()?.contentLength() ?: 0L
            TrafficStats.bytesSent += toSend

            val response = chain.proceed(request)
            // count response body size
            val rec = response.body()?.contentLength() ?: 0L
            TrafficStats.bytesReceived += rec

            return response
        }
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(contentTypeInterceptor)
        .addNetworkInterceptor(TrafficInterceptor())
        .build()

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}