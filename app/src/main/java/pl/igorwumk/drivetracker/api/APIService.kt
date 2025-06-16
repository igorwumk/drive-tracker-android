package pl.igorwumk.drivetracker.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface APIService {
    // Registration
    @POST("register/")
    fun register(@Body registrationRequest: RegistrationRequest): Call<UserResponse>

    // Login
    @POST("login/")
    fun login(@Body loginRequest: LoginRequest): Call<LoginResponse>

    // Logout (use token in header for authorization)
    @POST("logout/")
    fun logout(@Header("Authorization") authToken: String): Call<ResponseBody>
}
