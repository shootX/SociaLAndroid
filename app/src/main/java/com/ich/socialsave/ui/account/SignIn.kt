package com.ich.socialsave.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.ich.socialsave.R
import com.ich.socialsave.account.AccountState
import com.ich.socialsave.account.AppleSignIn
import com.ich.socialsave.account.FacebookSignIn
import com.ich.socialsave.account.GoogleSignIn
import com.ich.socialsave.account.SignInStatus
import com.ich.socialsave.container
import com.ich.socialsave.ui.components.openLink
import com.ich.socialsave.ui.settings.legalUrl
import com.ich.socialsave.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The sign-in sheet from Settings: app icon, title, subtitle, providers, legal text and "Not now". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(onDismiss: () -> Unit) {
    val c = Brand.colors
    val status by LocalContext.current.container.account.signInStatus.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.canvas,
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.brand_mark),
                null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(16.dp))
            SignInHeadline(TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            SignInOptions(onSignedIn = onDismiss)
            TextButton(
                onClick = onDismiss,
                enabled = status != SignInStatus.WORKING,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                NotNowLabel(enabled = status != SignInStatus.WORKING)
            }
        }
    }
}

/** "Not now" under the providers, dimmed while a sign-in is running (the button is disabled then). */
@Composable
fun NotNowLabel(enabled: Boolean) {
    val c = Brand.colors
    Text(
        stringResource(R.string.sign_in_not_now),
        color = if (enabled) c.gray else c.gray.copy(alpha = 0.4f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun SignInHeadline(align: TextAlign, titleSize: Int = 24) {
    val c = Brand.colors
    Text(
        stringResource(R.string.sign_in_title),
        color = c.ink,
        fontSize = titleSize.sp,
        lineHeight = (titleSize + 6).sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.sign_in_subtitle),
        color = c.gray,
        fontSize = if (titleSize > 24) 17.sp else 15.sp,
        lineHeight = if (titleSize > 24) 23.sp else 21.sp,
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Apple (black, white in dark theme), Google and Facebook (blue) buttons, the error line and the legal text
 * with Terms/Privacy links. [onSignedIn] runs once the account becomes usable.
 */
@Composable
fun SignInOptions(onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val account = context.container.account
    val c = Brand.colors
    val scope = rememberCoroutineScope()
    val state by account.state.collectAsStateWithLifecycle()
    val status by account.signInStatus.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    val busy = status == SignInStatus.WORKING

    LaunchedEffect(Unit) { account.clearSignInError() }
    LaunchedEffect(state) {
        if ((state as? AccountState.SignedIn)?.expired == false) signedIn()
    }

    fun google() {
        val activity = context.findActivity() ?: return account.reportSignInFailure()
        account.clearSignInError()
        scope.launch {
            try {
                GoogleSignIn.idToken(activity)?.let(account::signInWithGoogle)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("SignIn", "Google sign-in failed", error)
                account.reportSignInFailure()
            }
        }
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProviderButton(
                text = stringResource(R.string.sign_in_apple),
                logo = R.drawable.ic_logo_apple,
                enabled = !busy,
                // Apple's guideline: black on a light background, white on a dark one.
                container = if (c.isDark) Color.White else Color.Black,
                content = if (c.isDark) Color.Black else Color.White,
                border = null,
                tintLogo = true,
            ) {
                if (!AppleSignIn.start(context, account.appleStartUrl())) account.reportSignInFailure()
            }
            ProviderButton(
                text = stringResource(R.string.sign_in_google),
                logo = R.drawable.ic_logo_google,
                enabled = !busy,
                container = c.surface,
                content = c.ink,
                border = BorderStroke(1.dp, c.line),
                onClick = ::google,
            )
            if (FacebookSignIn.available) {
                val facebook = rememberFacebookLogin(
                    onToken = account::signInWithFacebook,
                    onError = account::reportSignInFailure,
                )
                ProviderButton(
                    text = stringResource(R.string.sign_in_facebook),
                    logo = R.drawable.ic_logo_facebook,
                    enabled = !busy,
                    container = FacebookBlue,
                    content = Color.White,
                    border = null,
                    tintLogo = true,
                ) {
                    account.clearSignInError()
                    facebook()
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(top = 10.dp), contentAlignment = Alignment.Center) {
            when (status) {
                SignInStatus.WORKING -> CircularProgressIndicator(color = c.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                SignInStatus.FAILED -> Text(
                    stringResource(R.string.sign_in_failed),
                    color = c.danger,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                SignInStatus.IDLE -> Unit
            }
        }
        LegalText()
    }
}

@Composable
private fun LegalText() {
    val context = LocalContext.current
    val c = Brand.colors
    val language = context.resources.configuration.locales[0].language
    Text(
        stringResource(R.string.sign_in_legal),
        color = c.gray,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { openLink(context, legalUrl("terms", language)) }) {
            Text(stringResource(R.string.settings_terms), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("·", color = c.gray, fontSize = 12.sp)
        TextButton(onClick = { openLink(context, legalUrl("privacy", language)) }) {
            Text(stringResource(R.string.settings_privacy), color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ProviderButton(
    text: String,
    logo: Int,
    enabled: Boolean,
    container: Color,
    content: Color,
    border: BorderStroke?,
    tintLogo: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Brand.controlRadius),
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(
            painterResource(logo),
            null,
            tint = if (tintLogo) content else Color.Unspecified,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private val FacebookBlue = Color(0xFF1877F2)

/** Facebook Login with the classic access token (the API verifies it with Graph `debug_token`). */
@Composable
private fun rememberFacebookLogin(onToken: (String) -> Unit, onError: () -> Unit): () -> Unit {
    val deliverToken by rememberUpdatedState(onToken)
    val reportError by rememberUpdatedState(onError)
    val callbacks = remember { CallbackManager.Factory.create() }
    val manager = remember { LoginManager.getInstance() }
    DisposableEffect(manager) {
        manager.registerCallback(
            callbacks,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    val accessToken = result.accessToken.token
                    FacebookSignIn.forgetToken()
                    deliverToken(accessToken)
                }

                override fun onCancel() = Unit

                override fun onError(error: FacebookException) {
                    android.util.Log.w("SignIn", "Facebook sign-in failed", error)
                    reportError()
                }
            },
        )
        onDispose { manager.unregisterCallback(callbacks) }
    }
    val launcher = rememberLauncherForActivityResult(manager.createLogInActivityResultContract(callbacks, null)) {}
    return { launcher.launch(listOf("public_profile", "email")) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
