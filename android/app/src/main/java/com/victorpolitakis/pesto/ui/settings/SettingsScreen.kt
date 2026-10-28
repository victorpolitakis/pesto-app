package com.victorpolitakis.pesto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.victorpolitakis.pesto.R
import com.victorpolitakis.pesto.data.AppSettings
import com.victorpolitakis.pesto.data.Languages
import com.victorpolitakis.pesto.data.ProfilesCatalog
import com.victorpolitakis.pesto.nav.SettingsSection
import com.victorpolitakis.pesto.overlay.OverlayMethod
import com.victorpolitakis.pesto.permissions.Requirement
import com.victorpolitakis.pesto.permissions.labelOf
import com.victorpolitakis.pesto.ui.components.PestoTopBar
import com.victorpolitakis.pesto.ui.theme.PestoTheme

/** One permission of C3, and whether Android granted it. */
data class RequirementStatus(val requirement: Requirement, val granted: Boolean)

/** Whether the backend answers its /health. */
enum class BackendStatus { CHECKING, ANSWERS, NO_ANSWER }

/** The last pairing made from this screen. */
enum class PairingStatus { IDLE, PAIRING, WRONG_CODE, FAILED, PAIRED }

/** Everything Settings show (C11). [paired]: yes or no, never the token (Δ24). */
data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val permissions: List<RequirementStatus> = emptyList(),
    val isXiaomi: Boolean = false,
    val backendUrl: String = "",
    val backend: BackendStatus = BackendStatus.CHECKING,
    val paired: Boolean = false,
    val pairing: PairingStatus = PairingStatus.IDLE,
    val dictations: Int = 0,
    val confirmDeleteAll: Boolean = false,
    val version: String = "",
    val buildType: String = "",
    val loading: Boolean = true,
)

/** What the screen asks for. The route gives the functions of the ViewModel, the tests their own. */
class SettingsActions(
    val onPrimaryLanguage: (String) -> Unit = {},
    val onSecondaryLanguage: (String?) -> Unit = {},
    val onStrictLanguages: (Boolean) -> Unit = {},
    val onDefaultProfile: (String) -> Unit = {},
    val onOverlayMethod: (OverlayMethod) -> Unit = {},
    val onRequest: (Requirement) -> Unit = {},
    val onCheckBackend: () -> Unit = {},
    val onPair: (String) -> Unit = {},
    val onUnpair: () -> Unit = {},
    val onAskDeleteAll: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
    val onDismissDeleteAll: () -> Unit = {},
)

/** [section]: a SettingsSection name to open at (Home's Languages, C3's pairing), or null for the top. */
@Composable
fun SettingsRoute(
    section: String?,
    onBack: () -> Unit,
    onRequest: (Requirement) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The code of the pairing stays on the screen only, never in a log.
    var code by rememberSaveable { mutableStateOf("") }
    // Back from a page of Android: what it granted meanwhile, and the backend again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.paired) { if (state.paired) code = "" }
    val actions = remember(viewModel, onRequest) {
        SettingsActions(
            onPrimaryLanguage = viewModel::setPrimaryLanguage,
            onSecondaryLanguage = viewModel::setSecondaryLanguage,
            onStrictLanguages = viewModel::setStrictLanguages,
            onDefaultProfile = viewModel::setDefaultProfile,
            onOverlayMethod = viewModel::setOverlayMethod,
            onRequest = onRequest,
            onCheckBackend = viewModel::checkBackend,
            onPair = viewModel::pair,
            onUnpair = viewModel::unpair,
            onAskDeleteAll = viewModel::askDeleteAll,
            onDeleteAll = viewModel::deleteAll,
            onDismissDeleteAll = viewModel::dismissDeleteAll,
        )
    }
    SettingsScreen(
        state,
        section = SettingsSection.entries.firstOrNull { it.name == section },
        code = code,
        onCodeChange = { code = it.filter(Char::isDigit).take(PAIRING_CODE_DIGITS) },
        actions = actions,
        onBack = onBack,
        modifier = modifier,
    )
}

private const val PAIRING_CODE_DIGITS = 8

/**
 * Settings (C11), one section after the other, in the order of SettingsSection;
 * [section] scrolls to one. The permissions are a list here, never a warning
 * elsewhere (C3 asks for them with Android's own dialogs).
 * TODO(phase 6): layout and design.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    section: SettingsSection?,
    code: String,
    onCodeChange: (String) -> Unit,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Xiaomi section only on Xiaomi devices (Δ13).
    val sections = SettingsSection.entries.filter { it != SettingsSection.XIAOMI || state.isXiaomi }
    val list = rememberLazyListState()
    LaunchedEffect(section, state.loading) {
        if (!state.loading && section != null && section in sections) list.scrollToItem(sections.indexOf(section))
    }
    Column(modifier.fillMaxSize().testTag("screen_settings")) {
        PestoTopBar(stringResource(R.string.title_settings), onBack)
        if (!state.loading) {
            LazyColumn(Modifier.fillMaxSize().testTag("settings"), state = list) {
                items(sections, key = { it.name }) { s ->
                    when (s) {
                        SettingsSection.LANGUAGES -> LanguagesSection(state.settings, actions)
                        SettingsSection.PROFILE -> ProfileSection(state.settings.defaultProfile, actions.onDefaultProfile)
                        SettingsSection.PERMISSIONS -> PermissionsSection(state.permissions, actions.onRequest)
                        SettingsSection.OVERLAY -> OverlaySection(state.settings.overlayMethod, actions.onOverlayMethod)
                        SettingsSection.XIAOMI -> XiaomiSection(onOpen = { actions.onRequest(Requirement.XIAOMI) })
                        SettingsSection.BACKEND -> BackendSection(state, code, onCodeChange, actions)
                        SettingsSection.PRIVACY -> PrivacySection(state.dictations, actions.onAskDeleteAll)
                        SettingsSection.ABOUT -> AboutSection(state.version, state.buildType)
                    }
                }
            }
        }
        if (state.confirmDeleteAll) DeleteAllDialog(onConfirm = actions.onDeleteAll, onDismiss = actions.onDismissDeleteAll)
    }
}

@Composable
private fun Section(title: String, tag: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(tag)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Explanation(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String, tag: String, modifier: Modifier = Modifier) {
    ListItem(headlineContent = { Text(label) }, modifier = modifier.testTag(tag), supportingContent = { Text(value) })
}

/** A setting with a few values: the row shows the current one, a tap opens the list. [options]: key and name. */
@Composable
private fun Picker(
    label: String,
    value: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ListItem(
            headlineContent = { Text(label) },
            modifier = Modifier.clickable { open = true }.testTag(tag),
            supportingContent = { Text(value) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((key, name) in options) {
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                    modifier = Modifier.testTag("${tag}_${key ?: "none"}"),
                )
            }
        }
    }
}

/** A radio row: the whole row is the touch target, as TalkBack reads it. */
@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, explanation: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(label)
            if (explanation != null) {
                Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun languageName(code: String): String = Languages.find(code)?.let { stringResource(it.name) } ?: code

/** Δ14: the primary, the second for code-switching, and whether only these. */
@Composable
private fun LanguagesSection(settings: AppSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_languages), "section_languages", modifier) {
        Explanation(stringResource(R.string.settings_languages_explained))
        Picker(
            stringResource(R.string.settings_primary_language),
            languageName(settings.primaryLanguage),
            Languages.all.map { it.code to stringResource(it.name) },
            onSelect = { code -> code?.let(actions.onPrimaryLanguage) },
            tag = "primary_language",
        )
        val none = stringResource(R.string.settings_no_language)
        Picker(
            stringResource(R.string.settings_secondary_language),
            settings.secondaryLanguage?.let { languageName(it) } ?: none,
            listOf<Pair<String?, String>>(null to none) +
                Languages.all.filter { it.code != settings.primaryLanguage }.map { it.code to stringResource(it.name) },
            onSelect = actions.onSecondaryLanguage,
            tag = "secondary_language",
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_strict)) },
            modifier = Modifier
                .toggleable(value = settings.strictLanguages, role = Role.Switch, onValueChange = actions.onStrictLanguages)
                .testTag("strict_languages"),
            supportingContent = { Text(stringResource(R.string.settings_strict_explained)) },
            trailingContent = { Switch(checked = settings.strictLanguages, onCheckedChange = null) },
        )
    }
}

@Composable
private fun ProfileSection(defaultProfile: String, onDefaultProfile: (String) -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_default_profile), "section_profile", modifier) {
        Explanation(stringResource(R.string.profile_default_explained))
        for (profile in ProfilesCatalog.all) {
            RadioRow(
                stringResource(profile.name),
                selected = profile.id == defaultProfile,
                onClick = { onDefaultProfile(profile.id) },
                Modifier.testTag("default_${profile.id}"),
            )
        }
    }
}

@Composable
private fun PermissionsSection(permissions: List<RequirementStatus>, onRequest: (Requirement) -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_permissions), "section_permissions", modifier) {
        for (p in permissions) {
            val name = p.requirement.name.lowercase()
            ListItem(
                headlineContent = { Text(stringResource(labelOf(p.requirement))) },
                modifier = Modifier.testTag("permission_$name"),
                supportingContent = {
                    Text(
                        stringResource(if (p.granted) R.string.settings_granted else R.string.settings_missing),
                        color = if (p.granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                },
                trailingContent = if (p.granted) {
                    null
                } else {
                    {
                        TextButton(onClick = { onRequest(p.requirement) }, modifier = Modifier.testTag("request_$name")) {
                            Text(stringResource(R.string.settings_open))
                        }
                    }
                },
            )
        }
    }
}

/** Δ12: method 1 by default, method 2 with what it costs. */
@Composable
private fun OverlaySection(method: OverlayMethod, onOverlayMethod: (OverlayMethod) -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_overlay), "section_overlay", modifier) {
        RadioRow(
            stringResource(R.string.settings_overlay_accessibility),
            selected = method == OverlayMethod.ACCESSIBILITY,
            onClick = { onOverlayMethod(OverlayMethod.ACCESSIBILITY) },
            Modifier.testTag("overlay_accessibility"),
        )
        RadioRow(
            stringResource(R.string.settings_overlay_application),
            selected = method == OverlayMethod.APPLICATION,
            onClick = { onOverlayMethod(OverlayMethod.APPLICATION) },
            Modifier.testTag("overlay_application"),
            explanation = stringResource(R.string.settings_overlay_application_explained),
        )
    }
}

/** Δ13: the page of C3 again, and what the system does not let the app check. */
@Composable
private fun XiaomiSection(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_xiaomi), "section_xiaomi", modifier) {
        Explanation(stringResource(R.string.settings_xiaomi_explained))
        OutlinedButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 16.dp).testTag("xiaomi_open")) {
            Text(stringResource(R.string.settings_xiaomi_open))
        }
    }
}

/** Δ24: where the backend is, whether it answers, and the pairing of this phone (never the token). */
@Composable
private fun BackendSection(
    state: SettingsState,
    code: String,
    onCodeChange: (String) -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Section(stringResource(R.string.settings_backend), "section_backend", modifier) {
        InfoRow(stringResource(R.string.settings_backend_url), state.backendUrl, "backend_url")
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backend_status)) },
            modifier = Modifier.testTag("backend_status"),
            supportingContent = {
                Text(
                    stringResource(
                        when (state.backend) {
                            BackendStatus.CHECKING -> R.string.settings_backend_checking
                            BackendStatus.ANSWERS -> R.string.settings_backend_answers
                            BackendStatus.NO_ANSWER -> R.string.settings_backend_no_answer
                        },
                    ),
                )
            },
            trailingContent = {
                TextButton(
                    onClick = actions.onCheckBackend,
                    enabled = state.backend != BackendStatus.CHECKING,
                    modifier = Modifier.testTag("check_backend"),
                ) { Text(stringResource(R.string.settings_backend_check)) }
            },
        )
        InfoRow(
            stringResource(R.string.settings_paired),
            stringResource(if (state.paired) R.string.settings_yes else R.string.settings_no),
            "paired",
        )
        if (state.paired) {
            if (state.pairing == PairingStatus.PAIRED) Explanation(stringResource(R.string.settings_pairing_done))
            Explanation(stringResource(R.string.settings_unpair_explained))
            OutlinedButton(onClick = actions.onUnpair, modifier = Modifier.padding(horizontal = 16.dp).testTag("unpair")) {
                Text(stringResource(R.string.settings_unpair))
            }
        } else {
            PairingForm(code, state.pairing, onCodeChange, actions.onPair)
        }
    }
}

@Composable
private fun PairingForm(
    code: String,
    status: PairingStatus,
    onCodeChange: (String) -> Unit,
    onPair: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canPair = code.length == PAIRING_CODE_DIGITS && status != PairingStatus.PAIRING
    val error = when (status) {
        PairingStatus.WRONG_CODE -> stringResource(R.string.settings_pairing_wrong)
        PairingStatus.FAILED -> stringResource(R.string.settings_pairing_failed)
        else -> null
    }
    Column(modifier) {
        Explanation(stringResource(R.string.settings_pairing_explained))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("pairing_code"),
            label = { Text(stringResource(R.string.settings_pairing_code)) },
            supportingText = if (error != null) {
                { Text(error, modifier = Modifier.testTag("pairing_error")) }
            } else {
                null
            },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canPair) onPair(code) }),
            singleLine = true,
        )
        Button(onClick = { onPair(code) }, enabled = canPair, modifier = Modifier.padding(horizontal = 16.dp).testTag("pair")) {
            Text(stringResource(R.string.settings_pair))
        }
    }
}

/** Δ26: what goes where, in plain words and without the names of the providers. Then the deletion of everything (Δ15). */
@Composable
private fun PrivacySection(dictations: Int, onAskDeleteAll: () -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_privacy), "section_privacy", modifier) {
        Explanation(stringResource(R.string.settings_privacy_audio))
        Explanation(stringResource(R.string.settings_privacy_text))
        Explanation(stringResource(R.string.settings_privacy_phone))
        Explanation(stringResource(R.string.settings_privacy_never))
        // TODO(experimental): export or backup of the data (JSON from the repositories, or Android Auto Backup
        // with rules for what leaves the phone). Until then allowBackup stays false.
        Text(
            pluralStringResource(R.plurals.dictations, dictations, dictations),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("history_size"),
        )
        OutlinedButton(
            onClick = onAskDeleteAll,
            enabled = dictations > 0,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("delete_all"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.settings_delete_all)) }
    }
}

@Composable
private fun AboutSection(version: String, buildType: String, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.settings_about), "section_about", modifier) {
        InfoRow(stringResource(R.string.settings_version), version, "version")
        InfoRow(stringResource(R.string.settings_build), buildType, "build_type")
    }
}

@Composable
private fun DeleteAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("delete_all_confirm"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.delete)) }
        },
        modifier = modifier.testTag("delete_all_dialog"),
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("delete_all_cancel")) { Text(stringResource(R.string.cancel)) }
        },
        text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
    )
}

private val sampleState = SettingsState(
    permissions = listOf(
        RequirementStatus(Requirement.MICROPHONE, granted = true),
        RequirementStatus(Requirement.ACCESSIBILITY, granted = false),
        RequirementStatus(Requirement.BATTERY, granted = true),
    ),
    backendUrl = "ws://127.0.0.1:8000/v1/dictate",
    backend = BackendStatus.ANSWERS,
    paired = true,
    dictations = 128,
    version = "0.3.0 (1)",
    buildType = "debug",
    loading = false,
)

@Composable
private fun ScreenPreview(state: SettingsState, code: String = "") {
    PestoTheme { SettingsScreen(state, section = null, code = code, onCodeChange = {}, actions = SettingsActions(), onBack = {}) }
}

@Preview
@Composable
private fun SettingsEmptyPreview() {
    ScreenPreview(SettingsState(loading = false))
}

@Preview(heightDp = 2400)
@Composable
private fun SettingsPreview() {
    ScreenPreview(sampleState)
}

@Preview(heightDp = 2600, fontScale = 1.5f)
@Composable
private fun SettingsLongGreekPreview() {
    ScreenPreview(
        sampleState.copy(
            settings = AppSettings(overlayMethod = OverlayMethod.APPLICATION),
            permissions = sampleState.permissions + RequirementStatus(Requirement.OVERLAY, granted = false),
            isXiaomi = true,
            backend = BackendStatus.NO_ANSWER,
            paired = false,
            pairing = PairingStatus.WRONG_CODE,
        ),
        code = "1234",
    )
}
