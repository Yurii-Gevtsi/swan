package com.gysignalstudio.blackswan.data.remote

import android.content.Context
import android.util.Log
import com.gysignalstudio.blackswan.data.local.AssetDataLoader
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@JsonClass(generateAdapter = true)
data class RemoteDataManifest(
    val schemaVersion: Int,
    val dataVersion: String,
    val generatedAt: String,
    val files: List<String>
)

/**
 * Downloads weekly data snapshots published on Firebase Hosting.
 *
 * Layout on hosting:
 *   /data/manifest.json  - version marker + list of data files
 *   /data/<name>.json    - the same JSON files the app bundles as assets
 *
 * Downloaded files are staged as *.tmp and committed together, so a failed
 * or interrupted sync never leaves a partially updated data directory.
 */
object RemoteDataSync {
    private const val TAG = "RemoteDataSync"
    private const val BASE_URL = "https://gy-signal-studio.web.app/data/"
    private const val MANIFEST_NAME = "manifest.json"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun fetchManifest(): RemoteDataManifest {
        val json = fetchText(BASE_URL + MANIFEST_NAME)
        return moshi.adapter(RemoteDataManifest::class.java).fromJson(json)
            ?: throw IOException("Manifest parsed to null")
    }

    /**
     * Downloads all [files] into the synced-data directory and commits them
     * atomically. The events snapshot is validated before commit so a corrupt
     * download can never replace a good local dataset.
     */
    fun downloadAndCommit(context: Context, files: List<String>) {
        val targetDir = AssetDataLoader.syncedDataDir(context)
        if (!targetDir.isDirectory && !targetDir.mkdirs()) {
            throw IOException("Cannot create ${targetDir.path}")
        }

        val staged = mutableListOf<Pair<File, File>>()
        try {
            for (name in files) {
                if (!isSafeFileName(name)) {
                    Log.w(TAG, "Skipping suspicious manifest entry: $name")
                    continue
                }
                val tmp = File(targetDir, "$name.tmp")
                downloadTo(BASE_URL + name, tmp)
                if (name == AssetDataLoader.EVENTS_ASSET) {
                    val events = AssetDataLoader.parseEvents(tmp.readText())
                    if (events.isEmpty()) {
                        throw IOException("Downloaded $name contains no events")
                    }
                }
                staged.add(tmp to File(targetDir, name))
            }
            for ((tmp, target) in staged) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    throw IOException("Cannot move ${tmp.name} into place")
                }
            }
            Log.i(TAG, "Committed ${staged.size} synced data files")
        } finally {
            for ((tmp, _) in staged) {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && name.endsWith(".json") && !name.contains('/') &&
            !name.contains('\\') && !name.contains("..")

    private fun fetchText(url: String): String = openConnection(url).let { connection ->
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.useCaches = false
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return connection
    }
}
