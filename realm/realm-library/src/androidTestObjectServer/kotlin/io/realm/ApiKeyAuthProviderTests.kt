/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm

import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.admin.ServerAdmin
import io.realm.log.LogLevel
import io.realm.log.RealmLog
import io.realm.rule.BlockingLooperThread
import org.bson.types.ObjectId
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiKeyAuthProviderTests {

    private val looperThread = BlockingLooperThread()
    private lateinit var app: TestRealmApp
    private lateinit var admin: ServerAdmin
    private lateinit var user: RealmUser
    private lateinit var provider: ApiKeyAuthProvider

    // Callback use to verify that an Illegal Argument was thrown from async methods
    private val checkNullInVoidCallback = object : RealmApp.Callback<Void> {
        override fun onSuccess(t: Void) {
            fail()
        }

        override fun onError(error: ObjectServerError) {
            assertEquals(ErrorCode.UNKNOWN, error.errorCode)
            looperThread.testComplete()
        }
    }
    private val checkNullInApiKeyCallback = object : RealmApp.Callback<RealmUserApiKey> {
        override fun onSuccess(t: RealmUserApiKey) {
            fail()
        }

        override fun onError(error: ObjectServerError) {
            assertEquals(ErrorCode.UNKNOWN, error.errorCode)
            looperThread.testComplete()
        }
    }

    // Methods exposed by the EmailPasswordAuthProvider
    enum class Method {
        CREATE,
        FETCH_SINGLE,
        FETCH_ALL,
        DELETE,
        ENABLE,
        DISABLE
    }

    @Before
    fun setUp() {
        app = TestRealmApp()
        RealmLog.setLevel(LogLevel.DEBUG)
        admin = ServerAdmin()
        user = app.registerUserAndLogin(TestHelper.getRandomEmail(), "123456")
        provider = app.apiKeyAuthProvider
    }

    @After
    fun tearDown() {
        app.close()
        admin.deleteAllUsers()
        RealmLog.setLevel(LogLevel.WARN)
    }

    inline fun testNullArg(method: () -> Unit) {
        try {
            method()
            fail()
        } catch (ignore: IllegalArgumentException) {
        }
    }

    @Test
    fun createApiKey() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        assertEquals("my-key", key.name)
        assertNotNull("my-key", key.value)
        assertNotNull("my-key", key.id)
        assertTrue("my-key", key.isEnabled)
    }

    @Test
    fun createApiKey_invalidServerArgsThrows() {
        try {
            provider.createApiKey("%s")
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.INVALID_PARAMETER, e.errorCode)
        }
    }

    @Test
    fun createApiKey_invalidArgumentThrows() {
        testNullArg { provider.createApiKey(TestHelper.getNullString()) }
        testNullArg { provider.createApiKey("") }
        looperThread.runBlocking {
            provider.createApiKeyAsync(TestHelper.getNullString(), checkNullInApiKeyCallback)
        }
        looperThread.runBlocking {
            provider.createApiKeyAsync("", checkNullInApiKeyCallback)
        }
    }

    @Test
    fun createApiKeyAsync() = looperThread.runBlocking {
        provider.createApiKeyAsync("my-key", object: RealmApp.Callback<RealmUserApiKey> {
            override fun onSuccess(key: RealmUserApiKey) {
                assertEquals("my-key", key.name)
                assertNotNull("my-key", key.value)
                assertNotNull("my-key", key.id)
                assertTrue("my-key", key.isEnabled)
                looperThread.testComplete()
            }

            override fun onError(error: ObjectServerError) {
                fail(error.toString())
            }
        })
    }

    @Test
    fun createApiKeyAsync_invalidServerArgsThrows() = looperThread.runBlocking {
        provider.createApiKeyAsync("%s", object: RealmApp.Callback<RealmUserApiKey> {
            override fun onSuccess(key: RealmUserApiKey) {
                fail()
            }

            override fun onError(e: ObjectServerError) {
                assertEquals(ErrorCode.INVALID_PARAMETER, e.errorCode)
                looperThread.testComplete()
            }
        })
    }

    @Test
    fun fetchApiKey() {
        val key1: RealmUserApiKey = provider.createApiKey("my-key")
        val key2: RealmUserApiKey = provider.fetchApiKey(key1.id)

        assertEquals(key1.id, key2.id)
        assertEquals(key1.name, key2.name)
        assertNull(key2.value)
        assertEquals(key1.isEnabled, key2.isEnabled)
    }

    @Test
    fun fetchApiKey_nonExistingKey() {
        try {
            provider.fetchApiKey(ObjectId())
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun fetchApiKey_invalidArgumentThrows() {
        testNullArg { provider.fetchApiKey(TestHelper.getNull()) }
        looperThread.runBlocking {
            provider.fetchApiKeyAsync(TestHelper.getNull(), checkNullInApiKeyCallback)
        }
    }

    @Test
    fun fetchApiKeyAsync() {
        val key1: RealmUserApiKey = provider.createApiKey("my-key")
        looperThread.runBlocking {
            provider.fetchApiKeyAsync(key1.id, object: RealmApp.Callback<RealmUserApiKey> {
                override fun onSuccess(key2: RealmUserApiKey) {
                    assertEquals(key1.id, key2.id)
                    assertEquals(key1.name, key2.name)
                    assertNull(key2.value)
                    assertEquals(key1.isEnabled, key2.isEnabled)
                    looperThread.testComplete()
                }

                override fun onError(error: ObjectServerError) {
                    fail(error.toString())
                }
            })
        }
    }

    @Test
    fun fetchAllApiKeys() {
        val key1: RealmUserApiKey = provider.createApiKey("my-key")
        val key2: RealmUserApiKey = provider.createApiKey("other-key")
        val allKeys: List<RealmUserApiKey> = provider.fetchAllApiKeys()
        assertEquals(2, allKeys.size)
        assertEquals(key1.id, allKeys[0].id)
        assertEquals(key2.id, allKeys[1].id)
    }

    @Test
    fun fetchAllApiKeysAsync() {
        val key1: RealmUserApiKey = provider.createApiKey("my-key")
        val key2: RealmUserApiKey = provider.createApiKey("other-key")
        looperThread.runBlocking {
            provider.fetchAllApiKeys(object: RealmApp.Callback<MutableList<RealmUserApiKey>> {
                override fun onSuccess(keys: MutableList<RealmUserApiKey>) {
                    assertEquals(2, keys.size)
                    assertEquals(key1.id, keys[0].id)
                    assertEquals(key2.id, keys[1].id)
                    looperThread.testComplete()
                }

                override fun onError(error: ObjectServerError) {
                    fail(error.toString())
                }
            })
        }
    }

    @Test
    fun deleteApiKey() {
        val key1: RealmUserApiKey = provider.createApiKey("my-key")
        assertNotNull(provider.fetchApiKey(key1.id))
        provider.deleteApiKey(key1.id)
        try {
            provider.fetchApiKey(key1.id)
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun deleteApiKey_invalidServerArgsThrows() {
        try {
            provider.deleteApiKey(ObjectId())
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun deleteApiKey_invalidArgumentThrows() {
        testNullArg { provider.deleteApiKey(TestHelper.getNull()) }
        looperThread.runBlocking {
            provider.deleteApiKeyAsync(TestHelper.getNull(), checkNullInVoidCallback)
        }
    }

    @Test
    fun deleteApiKeyAsync() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        assertNotNull(provider.fetchApiKey(key.id))
        looperThread.runBlocking {
            provider.deleteApiKeyAsync(key.id, object: RealmApp.Callback<Void> {
                override fun onSuccess(t: Void) {
                    try {
                        provider.fetchApiKey(key.id)
                        fail()
                    } catch (e: ObjectServerError) {
                        assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
                    }
                    looperThread.testComplete()
                }

                override fun onError(error: ObjectServerError) {
                    fail(error.toString())
                }

            })
        }
    }

    @Test
    fun deleteApiKeyAsync_invalidServerArgsThrows() = looperThread.runBlocking {
        provider.deleteApiKeyAsync(ObjectId(), object: RealmApp.Callback<Void> {
            override fun onSuccess(t: Void) {
                fail()
            }

            override fun onError(error: ObjectServerError) {
                assertEquals(ErrorCode.API_KEY_NOT_FOUND, error.errorCode)
                looperThread.testComplete()
            }
        })
    }

    @Test
    fun enableApiKey() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
        provider.enableApiKey(key.id)
        assertTrue(provider.fetchApiKey(key.id).isEnabled)
    }

    @Test
    fun enableApiKey_alreadyEnabled() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
        provider.enableApiKey(key.id)
        assertTrue(provider.fetchApiKey(key.id).isEnabled)
        provider.enableApiKey(key.id)
        assertTrue(provider.fetchApiKey(key.id).isEnabled)
    }

    @Test
    fun enableApiKey_invalidServerArgsThrows() {
        try {
            provider.enableApiKey(ObjectId())
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun enableApiKey_invalidArgumentThrows() {
        testNullArg { provider.enableApiKey(TestHelper.getNull()) }
        looperThread.runBlocking {
            provider.enableApiKeyAsync(TestHelper.getNull(), checkNullInVoidCallback)
        }
    }

    @Test
    fun enableApiKeyAsync() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
        looperThread.runBlocking {
            provider.enableApiKeyAsync(key.id, object: RealmApp.Callback<Void> {
                override fun onSuccess(t: Void) {
                    assertTrue(provider.fetchApiKey(key.id).isEnabled)
                    looperThread.testComplete()
                }

                override fun onError(error: ObjectServerError) {
                    fail(error.toString())
                }

            })
        }
    }

    @Test
    fun enableApiKeyAsync_invalidServerArgsThrows() = looperThread.runBlocking {
        provider.disableApiKeyAsync(ObjectId(), object: RealmApp.Callback<Void> {
            override fun onSuccess(t: Void) {
                fail()
            }

            override fun onError(error: ObjectServerError) {
                assertEquals(ErrorCode.API_KEY_NOT_FOUND, error.errorCode)
                looperThread.testComplete()
            }
        })
    }

    @Test
    fun disableApiKey() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
    }

    @Test
    fun disableApiKey_alreadyDisabled() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
        provider.disableApiKey(key.id)
        assertFalse(provider.fetchApiKey(key.id).isEnabled)
    }

    @Test
    fun disableApiKey_invalidServerArgsThrows() {
        try {
            provider.disableApiKey(ObjectId())
            fail()
        } catch (e: ObjectServerError) {
            assertEquals(ErrorCode.API_KEY_NOT_FOUND, e.errorCode)
        }
    }

    @Test
    fun disableApiKey_invalidArgumentThrows() {
        testNullArg { provider.disableApiKey(TestHelper.getNull()) }
        looperThread.runBlocking {
            provider.disableApiKeyAsync(TestHelper.getNull(), checkNullInVoidCallback)
        }
    }

    @Test
    fun disableApiKeyAsync() {
        val key: RealmUserApiKey = provider.createApiKey("my-key")
        assertTrue(key.isEnabled)
        looperThread.runBlocking {
            provider.disableApiKeyAsync(key.id, object: RealmApp.Callback<Void> {
                override fun onSuccess(t: Void) {
                    assertFalse(provider.fetchApiKey(key.id).isEnabled)
                    looperThread.testComplete()
                }

                override fun onError(error: ObjectServerError) {
                    fail(error.toString())
                }

            })
        }
    }

    @Test
    fun disableApiKeyAsync_invalidServerArgsThrows() = looperThread.runBlocking {
        provider.disableApiKeyAsync(ObjectId(), object: RealmApp.Callback<Void> {
            override fun onSuccess(t: Void) {
                fail()
            }

            override fun onError(error: ObjectServerError) {
                assertEquals(ErrorCode.API_KEY_NOT_FOUND, error.errorCode)
                looperThread.testComplete()
            }
        })
    }

    @Test
    @UiThreadTest
    fun callMethodsOnMainThreadThrows() {
        for (method in Method.values()) {
            try {
                when(method) {
                    Method.CREATE -> provider.createApiKey("name")
                    Method.FETCH_SINGLE -> provider.fetchApiKey(ObjectId())
                    Method.FETCH_ALL -> provider.fetchAllApiKeys()
                    Method.DELETE -> provider.deleteApiKey(ObjectId())
                    Method.ENABLE -> provider.enableApiKey(ObjectId())
                    Method.DISABLE -> provider.disableApiKey(ObjectId())
                }
                fail("$method should have thrown an exception")
            } catch (error: ObjectServerError) {
                assertEquals(ErrorCode.NETWORK_UNKNOWN, error.errorCode)
            }
        }
    }

    @Test
    fun callAsyncMethodsOnNonLooperThreadThrows() {
        val callback = object: RealmApp.Callback<Void> {
            override fun onSuccess(t: Void) { fail() }
            override fun onError(error: ObjectServerError) { fail() }
        }
        for (method in Method.values()) {
            try {
                when(method) {
                    Method.CREATE -> provider.createApiKeyAsync("key", object: RealmApp.Callback<RealmUserApiKey> {
                        override fun onSuccess(t: RealmUserApiKey) { fail() }
                        override fun onError(error: ObjectServerError) { fail() }
                    })
                    Method.FETCH_SINGLE -> provider.fetchApiKeyAsync(ObjectId(), object: RealmApp.Callback<RealmUserApiKey> {
                        override fun onSuccess(t: RealmUserApiKey) { fail() }
                        override fun onError(error: ObjectServerError) { fail() }
                    })
                    Method.FETCH_ALL -> provider.fetchAllApiKeys(object: RealmApp.Callback<List<RealmUserApiKey>> {
                        override fun onSuccess(t: List<RealmUserApiKey>) { fail() }
                        override fun onError(error: ObjectServerError) { fail() }
                    })
                    Method.DELETE -> provider.deleteApiKeyAsync(ObjectId(), callback)
                    Method.ENABLE -> provider.enableApiKeyAsync(ObjectId(), callback)
                    Method.DISABLE -> provider.disableApiKeyAsync(ObjectId(), callback)
                }
                fail("$method should have thrown an exception")
            } catch (ignore: IllegalStateException) {
            }
        }
    }

    @Test
    fun callMethodWithLoggedOutUser() {
        user.logOut()
        for (method in Method.values()) {
            try {
                when(method) {
                    Method.CREATE -> provider.createApiKey("name")
                    Method.FETCH_SINGLE -> provider.fetchApiKey(ObjectId())
                    Method.FETCH_ALL -> provider.fetchAllApiKeys()
                    Method.DELETE -> provider.deleteApiKey(ObjectId())
                    Method.ENABLE -> provider.enableApiKey(ObjectId())
                    Method.DISABLE -> provider.disableApiKey(ObjectId())
                }
                fail("$method should have thrown an exception")
            } catch (error: ObjectServerError) {
                assertEquals(ErrorCode.INVALID_SESSION, error.errorCode)
            }
        }
    }

    @Test
    fun getUser() {
        assertEquals(app.currentUser(), provider.user)
    }

    @Test
    fun getApp() {
        assertEquals(app, provider.app)
    }
}

