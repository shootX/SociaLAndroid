package com.shoto.socialsave.account

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.shoto.socialsave.container
import com.shoto.socialsave.ui.MainActivity

/**
 * Receives `socialsave://auth/apple?code=…&state=…` (or `?error=…`) from the server at the end of the
 * Apple web flow, hands it to [AccountSession.completeApple] (which checks `state` and redeems the code with
 * the PKCE `code_verifier`), then closes the Custom Tab by bringing the app back to the front.
 */
class AppleAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        // NEW_TASK finds the app's own task even when a browser (not a Custom Tab) ran the flow in its task;
        // CLEAR_TOP then closes the Custom Tab sitting above MainActivity.
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }

    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "socialsave" || uri.host != "auth" || uri.path != "/apple") return
        container.account.completeApple(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
        )
    }
}
