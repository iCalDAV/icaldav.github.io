package com.icalendar.webdav.client

import com.icalendar.webdav.model.*
import com.icalendar.webdav.xml.MultiStatusParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WebDAV HTTP client built on OkHttp.
 *
 * Provides low-level WebDAV operations (PROPFIND, REPORT, GET, PUT, DELETE)
 * with support for Basic authentication.
 *
 * Uses raw HTTP approach which works reliably with iCloud
 * and other CalDAV servers.
 */
class WebDavClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val auth: DavAuth? = null
) {
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val icalMediaType = "text/calendar; charset=utf-8".toMediaType()
    private val parser = MultiStatusParser.INSTANCE

    companion object {
        /**
         * Create default OkHttpClient with sensible timeout settings.
         * Note: Uses followRedirects(false) - callers should use withAuth() for proper redirect handling.
         */
        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)  // 5 min for large calendars
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(false)  // Handle redirects manually to preserve auth
                .build()
        }

        /**
         * Create OkHttpClient with authentication that handles redirects properly.
         *
         * This is critical for iCloud and other CalDAV servers that redirect to
         * partition servers (e.g., caldav.icloud.com → p180-caldav.icloud.com).
         * Standard OkHttp redirect handling strips Authorization headers on cross-host
         * redirects for security. This method uses a network interceptor to preserve
         * auth headers on all requests including redirects.
         *
         * @param auth Authentication credentials to use
         * @return OkHttpClient configured for CalDAV with proper redirect handling
         */
        fun withAuth(auth: DavAuth): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)  // 5 min for large calendars
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(false)  // Handle redirects manually
                .addNetworkInterceptor { chain ->
                    // Add auth header to ALL requests including redirects
                    val request = chain.request().newBuilder()
                        .header("Authorization", auth.toAuthHeader())
                        .header("User-Agent", "iCalDAV/1.0")
                        .build()

                    var response = chain.proceed(request)

                    // Handle redirects manually to preserve auth headers
                    var redirectCount = 0
                    while (response.code in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                        val location = response.header("Location") ?: break

                        // Resolve relative URLs
                        val newUrl = request.url.resolve(location) ?: break

                        response.close()
                        val redirectRequest = request.newBuilder()
                            .url(newUrl)
                            .build()
                        response = chain.proceed(redirectRequest)
                        redirectCount++
                    }

                    response
                }
                .build()
        }

        /**
         * Normalize ETag by removing surrounding quotes if present.
         * ETags should be stored without quotes and quoted when sent in If-Match headers.
         */
        fun normalizeEtag(etag: String?): String? {
            if (etag == null) return null
            return etag.trim().removeSurrounding("\"")
        }

        /**
         * Format ETag for If-Match header (adds quotes if not present).
         */
        fun formatEtagForHeader(etag: String): String {
            val normalized = etag.trim().removeSurrounding("\"")
            return "\"$normalized\""
        }
    }

    /**
     * Perform PROPFIND request.
     *
     * @param url Target URL
     * @param body XML request body
     * @param depth Depth header (0, 1, or infinity)
     * @return Parsed multistatus response
     */
    fun propfind(
        url: String,
        body: String,
        depth: DavDepth = DavDepth.ZERO
    ): DavResult<MultiStatus> {
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(xmlMediaType))
            .header("Depth", depth.value)
            .header("Content-Type", "application/xml; charset=utf-8")
            .applyAuth()
            .build()

        return executeAndParse(request)
    }

    /**
     * Perform REPORT request (used for calendar-query, sync-collection, etc.).
     *
     * @param url Target URL
     * @param body XML request body
     * @param depth Depth header
     * @return Parsed multistatus response
     */
    fun report(
        url: String,
        body: String,
        depth: DavDepth = DavDepth.ONE
    ): DavResult<MultiStatus> {
        val request = Request.Builder()
            .url(url)
            .method("REPORT", body.toRequestBody(xmlMediaType))
            .header("Depth", depth.value)
            .header("Content-Type", "application/xml; charset=utf-8")
            .applyAuth()
            .build()

        return executeAndParse(request)
    }

    /**
     * Perform GET request to fetch a resource.
     *
     * @param url Resource URL
     * @return Response body as string
     */
    fun get(url: String): DavResult<String> {
        val request = Request.Builder()
            .url(url)
            .get()
            .applyAuth()
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                DavResult.success(response.body?.string() ?: "")
            } else {
                DavResult.httpError(response.code, response.message)
            }
        } catch (e: IOException) {
            DavResult.networkError(e)
        }
    }

    /**
     * Perform PUT request to create or update a resource.
     *
     * @param url Resource URL
     * @param body Content to upload (iCal data for events)
     * @param etag ETag for conditional update (If-Match)
     * @param contentType Content type (defaults to text/calendar)
     * @return Response with new ETag if successful
     */
    fun put(
        url: String,
        body: String,
        etag: String? = null,
        contentType: MediaType = icalMediaType
    ): DavResult<PutResponse> {
        val requestBuilder = Request.Builder()
            .url(url)
            .put(body.toRequestBody(contentType))
            .header("Content-Type", contentType.toString())
            .applyAuth()

        // Conditional update with ETag (normalize to handle both quoted and unquoted)
        etag?.let {
            requestBuilder.header("If-Match", formatEtagForHeader(it))
        }

        val request = requestBuilder.build()

        return try {
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful -> {
                    val newEtag = normalizeEtag(response.header("ETag"))
                    DavResult.success(PutResponse(response.code, newEtag))
                }
                response.code == 412 -> {
                    // Precondition Failed - ETag mismatch (conflict)
                    DavResult.httpError(412, "ETag conflict - resource was modified")
                }
                else -> {
                    DavResult.httpError(response.code, response.message)
                }
            }
        } catch (e: IOException) {
            DavResult.networkError(e)
        }
    }

    /**
     * Perform DELETE request to remove a resource.
     *
     * @param url Resource URL
     * @param etag ETag for conditional delete (If-Match)
     * @return Success or error
     */
    fun delete(
        url: String,
        etag: String? = null
    ): DavResult<Unit> {
        val requestBuilder = Request.Builder()
            .url(url)
            .delete()
            .applyAuth()

        // Conditional delete with ETag (normalize to handle both quoted and unquoted)
        etag?.let {
            requestBuilder.header("If-Match", formatEtagForHeader(it))
        }

        val request = requestBuilder.build()

        return try {
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful || response.code == 204 -> DavResult.success(Unit)
                response.code == 404 -> DavResult.success(Unit) // Already deleted
                response.code == 412 -> DavResult.httpError(412, "ETag conflict")
                else -> DavResult.httpError(response.code, response.message)
            }
        } catch (e: IOException) {
            DavResult.networkError(e)
        }
    }

    /**
     * Perform MKCALENDAR request to create a new calendar.
     *
     * @param url Calendar collection URL
     * @param body XML request body with calendar properties
     * @return Success or error
     */
    fun mkcalendar(url: String, body: String): DavResult<Unit> {
        val request = Request.Builder()
            .url(url)
            .method("MKCALENDAR", body.toRequestBody(xmlMediaType))
            .header("Content-Type", "application/xml; charset=utf-8")
            .applyAuth()
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 201) {
                DavResult.success(Unit)
            } else {
                DavResult.httpError(response.code, response.message)
            }
        } catch (e: IOException) {
            DavResult.networkError(e)
        }
    }

    /**
     * Execute request and parse multistatus response.
     */
    private fun executeAndParse(request: Request): DavResult<MultiStatus> {
        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            when {
                response.isSuccessful && body != null -> {
                    parser.parse(body)
                }
                response.isSuccessful -> {
                    DavResult.success(MultiStatus.EMPTY)
                }
                else -> {
                    DavResult.httpError(response.code, response.message)
                }
            }
        } catch (e: IOException) {
            DavResult.networkError(e)
        }
    }

    /**
     * Apply authentication to request builder.
     */
    private fun Request.Builder.applyAuth(): Request.Builder {
        auth?.let { credentials ->
            header("Authorization", credentials.toAuthHeader())
        }
        return this
    }
}

/**
 * Response from PUT operation.
 */
data class PutResponse(
    val statusCode: Int,
    val etag: String?
)

/**
 * WebDAV authentication credentials.
 *
 * SECURITY NOTE: Credentials are stored in memory. For production use,
 * consider using secure credential storage mechanisms provided by the
 * platform (e.g., Android Keystore, macOS Keychain).
 */
sealed class DavAuth {
    abstract fun toAuthHeader(): String

    /**
     * HTTP Basic authentication.
     *
     * @property username The username for authentication
     * @property password The password (sensitive - masked in toString())
     */
    data class Basic(
        val username: String,
        val password: String
    ) : DavAuth() {
        override fun toAuthHeader(): String {
            val credentials = "$username:$password"
            val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            return "Basic $encoded"
        }

        /**
         * Override toString to prevent accidental credential leakage in logs.
         */
        override fun toString(): String = "Basic(username=$username, password=****)"

        /**
         * Create a copy with cleared password for safe logging.
         */
        fun masked(): String = "$username:****"
    }

    /**
     * Bearer token authentication (for OAuth).
     *
     * @property token The bearer token (sensitive - masked in toString())
     */
    data class Bearer(val token: String) : DavAuth() {
        override fun toAuthHeader(): String = "Bearer $token"

        /**
         * Override toString to prevent accidental token leakage in logs.
         */
        override fun toString(): String = "Bearer(token=****)"
    }
}
