package com.example.kble.network

import com.example.kble.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

object KbleSupabase {
    var url: String = BuildConfig.SUPABASE_URL
    var anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    val isConfigured: Boolean
        get() = url.startsWith("https://") && !url.contains("your-project") && anonKey.isNotBlank() && anonKey != "your-anon-key"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            httpEngine = OkHttp.create()
            install(Postgrest)
            install(Realtime)
        }
    }
}
