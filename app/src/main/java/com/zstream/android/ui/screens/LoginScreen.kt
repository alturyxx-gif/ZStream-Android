package com.zstream.android.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zstream.android.R
import com.zstream.android.data.CryptoUtils
import com.zstream.android.theme.LocalZStreamTheme
import com.zstream.android.ui.LocalIsTv
import com.zstream.android.ui.components.themed.ZsButton
import com.zstream.android.ui.components.themed.ZsButtonVariant
import com.zstream.android.ui.components.themed.ZsIconButton
import com.zstream.android.ui.components.themed.ZsIconButtonVariant
import com.zstream.android.ui.components.themed.ZsStatusBanner
import com.zstream.android.ui.components.themed.ZsStatusBannerVariant
import com.zstream.android.ui.components.themed.ZsTextButton
import com.zstream.android.ui.components.themed.ZsTextField
import com.zstream.android.ui.navigation.rememberSafeNavigateBack

@Composable
fun LoginScreen(nav: NavController, vm: AccountViewModel = hiltViewModel()) {
    val theme = LocalZStreamTheme.current
    val isTv = LocalIsTv.current
    val authState by vm.authState.collectAsState()
    val bg = theme.colors.background
    val txt = theme.colors.type
    val scope = rememberCoroutineScope()
    val onBack = rememberSafeNavigateBack(nav, scope)
    val context = LocalContext.current

    // Screens: "login" | "register_show" | "register_confirm"
    var screen by remember { mutableStateOf("login") }
    var generatedMnemonic by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            // TV can have multiple cached logins; swap local progress/bookmarks to the newly active one.
            if (isTv) vm.onProfileActivated()
            onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(bg.main)) {
        ZsIconButton(
            onClick = {
                when (screen) {
                    "login" -> onBack()
                    else -> { screen = "login"; vm.clearError() }
                }
            },
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.login_back),
            variant = ZsIconButtonVariant.Ghost,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        when (screen) {
            "login" -> LoginPanel(
                vm = vm,
                txt = txt,
                bg = bg,
                authState = authState,
                isTv = isTv,
                onPhoneLogin = { nav.navigate("tvSync") },
                onRegister = {
                    generatedMnemonic = CryptoUtils.generateMnemonic(context)
                    screen = "register_show"
                    vm.clearError()
                },
            )
            "register_show" -> ShowPassphrasePanel(
                mnemonic = generatedMnemonic,
                txt = txt, bg = bg,
                onNext = { screen = "register_confirm" },
            )
            "register_confirm" -> ConfirmRegisterPanel(
                mnemonic = generatedMnemonic,
                vm = vm, txt = txt, bg = bg, authState = authState,
            )
        }
    }
}

//  Login panel 

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.LoginPanel(
    vm: AccountViewModel,
    txt: com.zstream.android.theme.Type,
    bg: com.zstream.android.theme.Background,
    authState: AuthState,
    isTv: Boolean,
    onPhoneLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("Android") }
    var showPassphrase by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val passphraseFieldFocusRequester = remember { FocusRequester() }
    val showPassphraseButtonFocusRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible

    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.login_sign_in), color = txt.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.login_description), color = txt.dimmed, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))

        ZsTextField(
            label = stringResource(R.string.login_device_name),
            value = deviceName,
            onValueChange = { deviceName = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            showKeyboardOnFocus = !isTv,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ZsTextField(
            label = stringResource(R.string.login_passphrase),
            value = passphrase,
            onValueChange = { passphrase = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            showKeyboardOnFocus = !isTv,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (passphrase.isNotBlank()) vm.login(passphrase, deviceName)
            }),
            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showPassphrase = !showPassphrase },
                    modifier = Modifier
                        .focusRequester(showPassphraseButtonFocusRequester)
                        .then(
                            if (isTv) {
                                Modifier.focusProperties { left = passphraseFieldFocusRequester }
                            } else Modifier
                        ),
                ) {
                    Icon(if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = txt.dimmed)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passphraseFieldFocusRequester)
                .then(
                    if (isTv) {
                        Modifier
                            .focusProperties { right = showPassphraseButtonFocusRequester }
                            .onPreviewKeyEvent { event ->
                                if (!imeVisible && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    showPassphraseButtonFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                    } else Modifier
                ),
        )

        if (authState is AuthState.Error) {
            ZsStatusBanner(
                message = (authState as AuthState.Error).message,
                variant = ZsStatusBannerVariant.Error,
            )
        }

        ZsButton(
            text = stringResource(R.string.login_sign_in),
            onClick = { vm.login(passphrase, deviceName) },
            enabled = authState !is AuthState.Loading && passphrase.isNotBlank() && deviceName.isNotBlank(),
            loading = authState is AuthState.Loading,
            modifier = Modifier.height(48.dp).padding(top = 10.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = txt.dimmed.copy(0.3f))
            Text(stringResource(R.string.login_or), color = txt.dimmed, fontSize = 12.sp)
            HorizontalDivider(Modifier.weight(1f), color = txt.dimmed.copy(0.3f))
        }

        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            ZsButton(
                text = stringResource(R.string.login_with_passkey),
                onClick = { vm.loginWithPasskey(context, deviceName.ifBlank { "Android" }) },
                enabled = authState !is AuthState.Loading,
                modifier = Modifier.fillMaxWidth().height(47.dp),
                variant = ZsButtonVariant.Secondary,
                leadingIcon = Icons.Default.Lock,
            )

            ZsButton(
                text = stringResource(R.string.login_create_with_passkey),
                onClick = { vm.registerWithPasskey(context, deviceName.ifBlank { "Android" }) },
                enabled = authState !is AuthState.Loading && deviceName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(47.dp),
                variant = ZsButtonVariant.Secondary,
                leadingIcon = Icons.Default.Lock,
            )
        }

        if (isTv) {
            ZsButton(
                text = stringResource(R.string.login_from_phone),
                onClick = onPhoneLogin,
                enabled = authState !is AuthState.Loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                buttonModifier = Modifier.fillMaxWidth(),
                variant = ZsButtonVariant.Secondary,
                leadingIcon = Icons.Default.PhoneAndroid,
            )
        }

        ZsTextButton(
            text = stringResource(R.string.login_create_account_prompt),
            onClick = onRegister,
        )
    }
}

//  Show generated passphrase 

@Composable
private fun BoxScope.ShowPassphrasePanel(
    mnemonic: String,
    txt: com.zstream.android.theme.Type,
    bg: com.zstream.android.theme.Background,
    onNext: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val clipboard = LocalClipboardManager.current
    val words = mnemonic.split(" ")
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.login_your_passphrase), color = txt.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.login_passphrase_instructions),
            color = txt.dimmed, fontSize = 13.sp,
        )

        // Word grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg.secondary.copy(0.4f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words) { i, word ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(bg.secondary).padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.login_passphrase_word, i + 1, word), color = txt.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (!isTv)
        {
            ZsButton(
                text = stringResource(if (copied) R.string.login_copied else R.string.login_copy_to_clipboard),
                onClick = { clipboard.setText(AnnotatedString(mnemonic)); copied = true },
                modifier = Modifier.height(44.dp),
                variant = if (copied) ZsButtonVariant.Primary else ZsButtonVariant.Secondary,
                leadingIcon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            )
        }

        ZsButton(
            text = stringResource(R.string.login_saved_continue),
            onClick = onNext,
            modifier = Modifier.height(48.dp),
        )
    }
}

//  Confirm + register 

@Composable
private fun BoxScope.ConfirmRegisterPanel(
    mnemonic: String,
    vm: AccountViewModel,
    txt: com.zstream.android.theme.Type,
    bg: com.zstream.android.theme.Background,
    authState: AuthState,
) {
    val isTv = LocalIsTv.current
    var deviceName by remember { mutableStateOf("Android") }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.login_create_account), color = txt.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.login_create_description), color = txt.dimmed, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))

        ZsTextField(
            label = stringResource(R.string.login_device_name),
            value = deviceName,
            onValueChange = { deviceName = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            showKeyboardOnFocus = !isTv,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                vm.register(mnemonic, deviceName)
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (authState is AuthState.Error) {
            ZsStatusBanner(
                message = (authState as AuthState.Error).message,
                variant = ZsStatusBannerVariant.Error,
            )
        }

        ZsButton(
            text = stringResource(R.string.login_create_account),
            onClick = { vm.register(mnemonic, deviceName) },
            enabled = authState !is AuthState.Loading && deviceName.isNotBlank(),
            loading = authState is AuthState.Loading,
            modifier = Modifier.height(48.dp),
        )

        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            ZsButton(
                text = stringResource(R.string.login_create_with_passkey_short),
                onClick = { vm.registerWithPasskey(context, deviceName.ifBlank { "Android" }) },
                enabled = authState !is AuthState.Loading && deviceName.isNotBlank(),
                modifier = Modifier.height(48.dp),
                variant = ZsButtonVariant.Secondary,
                leadingIcon = Icons.Default.Lock,
            )
        }
    }
}
