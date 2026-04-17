package com.team404.dualshield.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// ── Request Models ──────────────────────────────────────────────────────────

data class RegisterRequest(
    val name: String,
    val phone: String,
    val emergency_name: String = "",
    val emergency_phone: String = ""
)

data class LoginRequest(val phone: String)

data class ContactRequest(
    val contact_name: String,
    val contact_phone: String,
    val relation: String = "Family"
)

data class SyncRequest(
    val phone: String,
    val name: String,
    val contacts: List<ContactItem>
)

data class IncidentReport(
    val userId: String,
    val phone: String = "",
    val latitude: Double,
    val longitude: Double,
    val severityLevel: Int,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val timestamp: Long
)

// ── Response Models ─────────────────────────────────────────────────────────

data class AuthResponse(
    val status: String,
    val user_id: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val message: String? = null
)

data class IncidentResponse(
    val status: String,
    val incident_id: String? = null,
    val message: String? = null
)

data class IncidentItem(
    val id: String = "",
    val userId: String = "",
    val phone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val severityLevel: Int = 1,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val timestamp: Long = 0L,
    val received_at: String = ""
)

data class Zone(
    val name: String = "Unknown Zone",
    val lat: Double,
    val lng: Double,
    val radius: Float,
    val risk: String? = "Moderate"
)

data class ContactItem(
    val contact_name: String = "",
    val contact_phone: String = "",
    val relation: String = "Family"
)

data class HealthResponse(
    val status: String,
    val database: String,
    val version: String
)

// ── API Interface ───────────────────────────────────────────────────────────

interface BackendApi {

    // Auth
    @POST("api/users/register")
    suspend fun registerUser(@Body user: RegisterRequest): Response<AuthResponse>

    @POST("api/users/login")
    suspend fun loginUser(@Body req: LoginRequest): Response<AuthResponse>

    // Incidents
    @POST("api/incidents")
    suspend fun reportIncident(@Body report: IncidentReport): Response<IncidentResponse>

    @GET("api/incidents")
    suspend fun getIncidents(
        @Query("userId") userId: String? = null,
        @Query("phone") phone: String? = null
    ): Response<List<IncidentItem>>

    // Geofences
    @GET("api/geofences/accident-zones")
    suspend fun getAccidentZones(): Response<List<Zone>>

    // Contacts
    @GET("api/users/{phone}/contacts")
    suspend fun getContacts(@Path("phone") phone: String): Response<List<ContactItem>>

    @POST("api/users/{phone}/contacts")
    suspend fun addContact(@Path("phone") phone: String, @Body contact: ContactRequest): Response<Map<String, String>>

    @DELETE("api/users/{phone}/contacts/{contactPhone}")
    suspend fun deleteContact(@Path("phone") phone: String, @Path("contactPhone") contactPhone: String): Response<Map<String, String>>

    // Health
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("api/users/sync")
    suspend fun syncUserData(@Body request: SyncRequest): Response<AuthResponse>

    companion object {
        private const val BASE_URL = "https://dualshield-live-v3.loca.lt/"

        fun create(): BackendApi {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("bypass-tunnel-reminder", "true")
                        .build()
                    chain.proceed(request)
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BackendApi::class.java)
        }
    }
}
