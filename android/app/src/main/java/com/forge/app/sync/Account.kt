package com.forge.app.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.forge.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in and the Firebase project behind sync. The project's ids come in at build
 * time (android/firebase.properties); without them sync stays hidden and nothing changes.
 */
object Account {

    private const val APP_NAME = "forge"

    /** Device tests point sync at the Firebase emulator through this. */
    @Volatile internal var testApp: FirebaseApp? = null

    val configured: Boolean
        get() = testApp != null || BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_WEB_CLIENT_ID.isNotBlank()

    fun app(c: Context): FirebaseApp? {
        testApp?.let { return it }
        if (!configured) return null
        val ctx = c.applicationContext
        return FirebaseApp.getApps(ctx).firstOrNull { it.name == APP_NAME } ?: synchronized(this) {
            FirebaseApp.getApps(ctx).firstOrNull { it.name == APP_NAME } ?: FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .build(),
                APP_NAME
            )
        }
    }

    fun auth(c: Context): FirebaseAuth? = app(c)?.let { FirebaseAuth.getInstance(it) }

    fun firestore(c: Context): FirebaseFirestore? = app(c)?.let { FirebaseFirestore.getInstance(it) }

    data class User(val uid: String, val email: String, val name: String)

    fun user(c: Context): User? = try {
        auth(c)?.currentUser?.let { User(it.uid, it.email ?: "", it.displayName ?: "") }
    } catch (_: Exception) { null }

    sealed class SignIn {
        data class Ok(val user: User) : SignIn()
        object Cancelled : SignIn()
        data class Failed(val message: String) : SignIn()
    }

    /** Shows Google's account picker and signs in to Firebase with the chosen account. */
    suspend fun signIn(activity: Activity): SignIn {
        val auth = auth(activity) ?: return SignIn.Failed("Sync isn't set up in this build")
        return try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.FIREBASE_WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignIn.Failed("Google didn't return an account")
            }
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            val signed = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user
                ?: return SignIn.Failed("Sign-in didn't finish")
            SignIn.Ok(User(signed.uid, signed.email ?: "", signed.displayName ?: ""))
        } catch (_: GetCredentialCancellationException) {
            SignIn.Cancelled
        } catch (e: Exception) {
            SignIn.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Signs out of this device. Data on the phone stays and nothing is deleted online. What
     * was synced is remembered, so signing back in to the same account doesn't double
     * anything; a different account starts fresh.
     */
    fun signOut(c: Context) {
        try { auth(c)?.signOut() } catch (_: Exception) {}
    }
}
