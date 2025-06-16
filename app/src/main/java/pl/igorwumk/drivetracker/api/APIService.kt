package pl.igorwumk.drivetracker.api

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
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

    // Shallow list of user's trackings
    @GET("trackings/")
    suspend fun listTrackings(@Header("Authorization") auth: String): Response<List<TrackingSessionDto>>

    // Complete data on one tracking session
    @GET("tracking/{id}/")
    suspend fun getTrackingDetail(@Path("id") id: Long, @Header("Authorization") auth: String): Response<FullTrackingSessionDto>

    // Remove tracking session from the server by its server-side id
    @DELETE("tracking/delete/{id}/")
    suspend fun deleteTracking(@Path("id") id: Long, @Header("Authorization") auth: String): Response<Unit>

    // Upload new tracking session
    @POST("tracking/new/")
    suspend fun createTracking(@Body req: TrackingSessionUploadDto, @Header("Authorization") auth: String): Response<TrackingSessionDto>

}
