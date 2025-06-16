package pl.igorwumk.drivetracker.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "http://192.168.1.20:8000/"

    // To force correct Content-Type
    val contentTypeInterceptor = Interceptor { chain ->
        val originalRequest: Request = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(newRequest)
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(contentTypeInterceptor)
        .build()

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}