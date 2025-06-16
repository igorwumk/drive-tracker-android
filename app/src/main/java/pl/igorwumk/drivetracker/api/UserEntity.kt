package pl.igorwumk.drivetracker.api

// Registration
data class RegistrationRequest(
    val username: String,
    val email: String,
    val password: String
)

// User object returned
data class UserResponse(
    val id: Int,
    val username: String,
    val email: String
)

// Login request
data class LoginRequest(
    val username: String,
    val password: String
)

// Login response
data class LoginResponse(
    val token: String,
    val user_id: Int,
    val username: String
)