package com.aidriy.app.data.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Talks to the real AIDRIY Supabase project without creating anything: a wrong password must be
 * refused with the right error, and anonymous reads must see no rows (Row Level Security).
 * Skipped (not passed) when the device has no internet.
 */
@RunWith(AndroidJUnit4::class)
class CloudLiveTest {

    @Test
    fun wrong_password_is_refused_as_invalid_credentials() = runBlocking {
        val result = CloudAuth().signIn("tidak-ada-akun@example.com", "kata-sandi-salah-123")
        assumeTrue("no internet on this device", (result as? AuthResult.Failed)?.error != AuthError.Offline)
        assertEquals(AuthResult.Failed(AuthError.InvalidCredentials), result)
    }

    @Test
    fun anonymous_reads_return_no_rows() = runBlocking {
        val response = try {
            CloudHttp.request("GET", "${CloudConfig.URL}/rest/v1/notes?select=id&limit=1", mapOf("Accept" to "application/json"))
        } catch (e: CloudOfflineException) {
            assumeTrue("no internet on this device", false)
            return@runBlocking
        }
        assertEquals(200, response.code)
        assertEquals("[]", response.body.trim())
    }
}
