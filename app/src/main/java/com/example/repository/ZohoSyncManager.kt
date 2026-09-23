package com.example.repository

import android.util.Log
import com.example.data.MovieRecord
import com.example.parser.FileImporter
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ZohoFileItem(
    val id: String,
    val name: String,
    val extension: String
)

object ZohoSyncManager {
    private const val TAG = "ZohoSyncManager"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Refreshes the Zoho OAuth v2 access token using the specified accounts server base URL.
     * Throws an Exception if the request fails, wrapping Zoho's error payload.
     */
    fun refreshAccessToken(
        accountsServer: String,
        clientId: String,
        clientSecret: String,
        refreshToken: String
    ): String {
        if (clientId.isBlank()) throw Exception("Client ID is blank.")
        if (clientSecret.isBlank()) throw Exception("Client Secret is blank.")
        if (refreshToken.isBlank()) throw Exception("Refresh Token is blank.")

        // Standardize accounts server url (remove trailing slash)
        val serverBase = accountsServer.trim().removeSuffix("/")
        val url = "$serverBase/oauth/v2/token"

        val requestBody = FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("client_secret", clientSecret.trim())
            .add("refresh_token", refreshToken.trim())
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "Refresh Token Response code: ${response.code}, body: $bodyStr")
                
                if (bodyStr == null) {
                    throw Exception("Auth server returned empty response (HTTP ${response.code}).")
                }

                if (bodyStr.trim().startsWith("<html") || bodyStr.trim().startsWith("<!DOCTYPE")) {
                    // Extract title from HTML or keep first 200 characters
                    val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                    val titleMatch = titleRegex.find(bodyStr)
                    val titleText = titleMatch?.groups?.get(1)?.value ?: "HTML error response"
                    val preview = bodyStr.take(150)
                    throw Exception("Auth server returned HTML instead of JSON (HTTP ${response.code}): $titleText ($preview...)")
                }
                
                val json = try {
                    JSONObject(bodyStr)
                } catch (pe: Exception) {
                    throw Exception("Invalid response format received from Zoho (HTTP ${response.code}). Please check your endpoint or Client configuration.")
                }

                if (!response.isSuccessful) {
                    val errorMsg = json.optString("error", null) ?: json.optString("message", "HTTP status ${response.code}")
                    throw Exception("Zoho OAuth Error ($errorMsg) on host: $serverBase")
                }
                
                val token = json.optString("access_token", "")
                if (token.isEmpty()) {
                    val errorMsg = json.optString("error", null) ?: "No access_token field present in JSON."
                    throw Exception("Zoho Auth Failure: $errorMsg")
                }
                
                return token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Zoho Access Token", e)
            if (e.message?.contains("Zoho") == true || e.message?.contains("Auth") == true || e.message?.contains("HTML") == true) {
                throw e
            }
            throw Exception("Failed to connect/refresh Zoho token: ${e.message ?: "network error"}", e)
        }
    }

    fun getZohoDomain(accountsServer: String): String {
        val clean = accountsServer.trim().lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        
        return if (clean.startsWith("accounts.")) {
            val d = clean.substringAfter("accounts.")
            if (d.isNotEmpty()) d else "zoho.com"
        } else {
            if (clean.contains("zoho.")) {
                clean.substring(clean.indexOf("zoho."))
            } else if (clean.contains("zohocloud.")) {
                clean.substring(clean.indexOf("zohocloud."))
            } else {
                "zoho.com"
            }
        }
    }

    /**
     * Fetches files and folders info under [folderId] in Zoho Workrive.
     */
    fun fetchFilesInFolder(
        apiBaseUrl: String,
        folderId: String,
        accessToken: String
    ): List<ZohoFileItem> {
        val serverBase = apiBaseUrl.trim().removeSuffix("/")
        val result = mutableListOf<ZohoFileItem>()
        var offset = 0
        val limit = 50

        while (true) {
            // Build URL matching the working Java list API path exactly:
            // baseUrl + "/files/" + folderId + "/files" + "?" + limit + "&page%5Boffset%5D="+ offset
            val url = "$serverBase/files/$folderId/files?$limit&page%5Boffset%5D=$offset"
            Log.d(TAG, "fetchFilesInFolder request URL: $url")

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Zoho-oauthtoken $accessToken")
                .header("Accept", "application/vnd.api+json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "Fetch Files Response code: ${response.code}, body: ${bodyStr?.take(1000)}")
                
                if (!response.isSuccessful || bodyStr == null) {
                    val preview = bodyStr?.take(300) ?: "empty response body"
                    if (preview.trim().startsWith("<html") || preview.trim().startsWith("<!DOCTYPE")) {
                        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                        val titleMatch = titleRegex.find(preview)
                        val titleText = titleMatch?.groups?.get(1)?.value ?: "HTML error page"
                        throw Exception("Zoho API returned HTML Error (HTTP ${response.code}): $titleText")
                    }
                    throw Exception("Zoho list files request failed [HTTP ${response.code}]: $preview")
                }

                if (bodyStr.trim().startsWith("<html") || bodyStr.trim().startsWith("<!DOCTYPE")) {
                    val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                    val titleMatch = titleRegex.find(bodyStr)
                    val titleText = titleMatch?.groups?.get(1)?.value ?: "HTML error page"
                    throw Exception("Zoho API returned HTML Error where JSON was expected (HTTP ${response.code}): $titleText")
                }

                val json = try {
                    JSONObject(bodyStr)
                } catch (pe: Exception) {
                    throw Exception("Failed to parse list files API response to JSON (HTTP ${response.code}). Content: ${bodyStr.take(150)}...")
                }

                // Check for API errors inside structured JSON
                val errors = json.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    val firstErr = errors.optJSONObject(0)
                    val detail = firstErr?.optString("detail", "") ?: firstErr?.optString("title", "Unknown API error")
                    throw Exception("Zoho List Files API Error: $detail")
                }

                val dataArray = json.optJSONArray("data")
                if (dataArray == null || dataArray.length() == 0) {
                    return@use
                }

                for (i in 0 until dataArray.length()) {
                    val itemObj = dataArray.optJSONObject(i) ?: continue
                    val id = itemObj.optString("id", "")
                    val attributes = itemObj.optJSONObject("attributes") ?: continue
                    
                    val isFolder = attributes.optBoolean("is_folder", false)
                    if (isFolder) {
                        Log.d(TAG, "Skipping nested folder: ${attributes.optString("name")}")
                        continue
                    }

                    val name = attributes.optString("name", "")
                    val extension = attributes.optString("extn", "") // Key is "extn" in the WorkDrive API attributes dictionary

                    if (id.isNotEmpty()) {
                        result.add(ZohoFileItem(id = id, name = name, extension = extension))
                    }
                }

                if (dataArray.length() < limit) {
                    return result
                }
                offset += limit
            }
        }
    }

    /**
     * Downloads file content stream as raw bytes from Workdrive.
     */
    fun downloadFileContent(
        downloadBaseUrl: String,
        fileId: String,
        accessToken: String
    ): ByteArray {
        val serverBase = downloadBaseUrl.trim().removeSuffix("/")
        val url = "$serverBase/$fileId"
        Log.d(TAG, "downloadFileContent request URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Zoho-oauthtoken $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()?.take(500) ?: "No body returned."
                Log.e(TAG, "Download failed [HTTP ${response.code}]: $errorBody")
                if (errorBody.trim().startsWith("<html") || errorBody.trim().startsWith("<!DOCTYPE")) {
                    val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                    val titleMatch = titleRegex.find(errorBody)
                    val titleText = titleMatch?.groups?.get(1)?.value ?: "HTML error"
                    throw Exception("Download server returned HTML instead of spreadsheet bytes (HTTP ${response.code}): $titleText")
                }
                throw Exception("Failed to download file (HTTP ${response.code}): $errorBody")
            }
            val bytes = response.body?.bytes()
            if (bytes == null || bytes.isEmpty()) {
                throw Exception("Received an empty file stream payload from WorkDrive download endpoint.")
            }
            return bytes
        }
    }

    /**
     * Orchestrates Zoho Sheets Sync cycle completely, returning parsed records on success.
     * Throws explicit descriptive exceptions if failure points are reached.
     */
    fun performSync(
        accountsServer: String,
        apiServer: String,
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        folderId: String,
        configFileName: String,
        configExtension: String
    ): List<MovieRecord> {
        val targetNameClean = configFileName.trim().lowercase(Locale.ROOT)
        val targetExtClean = configExtension.trim().lowercase(Locale.ROOT).removePrefix(".")

        // 1. Authorization
        Log.i(TAG, "Step 1: Refreshing token...")
        val accessToken = refreshAccessToken(accountsServer, clientId, clientSecret, refreshToken)

        // Resolve Region Endpoint domains dynamically based on accountsServer
        val domain = getZohoDomain(accountsServer)
        val apiBaseUrl = "https://workdrive.$domain/api/v1"
        val downloadBaseUrl = "https://download-accl.$domain/v1/workdrive/download"

        Log.d(TAG, "Resolved region-specific API endpoint: $apiBaseUrl")
        Log.d(TAG, "Resolved region-specific Download endpoint: $downloadBaseUrl")

        // 2. Fetch Files List
        Log.i(TAG, "Step 2: Listing files inside folder $folderId...")
        val files = fetchFilesInFolder(apiBaseUrl, folderId, accessToken)
        if (files.isEmpty()) {
            throw Exception("No files found or listed in Zoho Folder structure. Ensure folder ID is correct and permissions allow reading.")
        }

        // 3. Match Configured Filename
        Log.i(TAG, "Step 3: Searching matching workbook for '$configFileName'...")
        var matchedFile: ZohoFileItem? = null

        for (file in files) {
            val fileNameClean = file.name.trim().lowercase(Locale.ROOT)
            val baseNameWithoutExt = if (fileNameClean.contains(".")) {
                fileNameClean.substringBeforeLast(".")
            } else {
                fileNameClean
            }

            val baseTargetWithoutExt = if (targetNameClean.contains(".")) {
                targetNameClean.substringBeforeLast(".")
            } else {
                targetNameClean
            }

            // Checks combinations for flexible matching
            val isExactMatch = fileNameClean == targetNameClean
            val isBaseNameMatch = baseNameWithoutExt == baseTargetWithoutExt
            val isMatchWithExtAppend = fileNameClean == "$targetNameClean.$targetExtClean"

            if (isExactMatch || isBaseNameMatch || isMatchWithExtAppend) {
                matchedFile = file
                break
            }
        }

        if (matchedFile == null) {
            val listedNames = files.joinToString(", ") { "${it.name} (${it.extension})" }
            throw Exception("No file matched configuration '$configFileName' with extension '$configExtension'. Files present in folder: [$listedNames]")
        }

        // 4. Download matched sheets file
        Log.i(TAG, "Step 4: Downloading file bytes for matched file id=${matchedFile.id}, name=${matchedFile.name}...")
        val fileBytes = downloadFileContent(downloadBaseUrl, matchedFile.id, accessToken)
            ?: throw Exception("Failed to retrieve file payload bytes from Zoho server download stream.")

        // 5. Parse downloaded spreadsheet (Excel .xlsx OR fallback CSV)
        Log.i(TAG, "Step 5: Invoking local excel parsing model on stream...")
        val fileInputStream = ByteArrayInputStream(fileBytes)
        val isCsv = matchedFile.name.endsWith(".csv") || matchedFile.name.endsWith(".txt") || matchedFile.extension.lowercase(Locale.ROOT) == "csv"

        val records = if (isCsv) {
            FileImporter.parseCsv(fileInputStream)
        } else {
            FileImporter.parseXlsx(fileInputStream)
        }

        if (records.isEmpty()) {
            throw Exception("Spreadsheet parse completed, but extracted 0 valid records. Verify the sheet layout, headers, or workbook data.")
        }

        Log.i(TAG, "Successfully synced ${records.size} movie records from Zoho Sheets!")
        return records
    }
}
