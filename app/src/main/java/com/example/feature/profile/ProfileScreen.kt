package com.example.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.core.ai.AiAvailability
import com.example.core.ai.AiProviderType
import com.example.core.common.ThemeMode
import com.example.core.common.TtsAccent
import com.example.feature.components.EnglishTextBlock
import com.example.feature.components.OfflineStatusPill

private enum class DataResetConfirmation {
  NONE,
  RESET_PROGRESS,
  CLEAR_ALL_LEARNING,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
  viewModel: ProfileViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val prefs = uiState.preferences
  var confirmationDialog by remember { mutableStateOf(DataResetConfirmation.NONE) }
  var endpointDraft by remember(prefs.aiEndpointUrl) { mutableStateOf(prefs.aiEndpointUrl) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("profile_settings_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.profile_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OfflineStatusPill(label = stringResource(R.string.status_offline_ready))
        }
      }

      uiState.actionFeedbackMessage?.let { feedback ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("profile_feedback_banner"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(text = feedback, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
              IconButton(onClick = viewModel::dismissFeedback, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن پیام")
              }
            }
          }
        }
      }

      // Theme Settings
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_theme_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_theme_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = prefs.themeMode == ThemeMode.SYSTEM,
                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                label = { Text(stringResource(R.string.profile_theme_system)) },
                modifier = Modifier.testTag("theme_chip_system"),
              )
              FilterChip(
                selected = prefs.themeMode == ThemeMode.LIGHT,
                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                label = { Text(stringResource(R.string.profile_theme_light)) },
                modifier = Modifier.testTag("theme_chip_light"),
              )
              FilterChip(
                selected = prefs.themeMode == ThemeMode.DARK,
                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                label = { Text(stringResource(R.string.profile_theme_dark)) },
                modifier = Modifier.testTag("theme_chip_dark"),
              )
            }
          }
        }
      }

      // TTS Speed, Accent & Local Voice Selector
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_tts_speed_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stringResource(R.string.profile_tts_speed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "${"%.2f".format(prefs.ttsSpeed)}x",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Slider(
              value = prefs.ttsSpeed,
              onValueChange = { viewModel.setTtsSpeed(it) },
              valueRange = 0.6f..1.5f,
              modifier = Modifier.testTag("profile_tts_speed_slider"),
            )
            Text(
              text = "لهجه پیش‌فرض تلفظ",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TtsAccent.entries.forEach { accent ->
                FilterChip(
                  selected = prefs.ttsAccent == accent,
                  onClick = { viewModel.setTtsAccent(accent) },
                  label = { Text(accent.persianLabel) },
                  modifier = Modifier.testTag("accent_chip_${accent.name.lowercase()}"),
                )
              }
            }

            HorizontalDivider()
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "انتخاب صدای محلی دستگاه",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              TextButton(onClick = viewModel::refreshVoices) {
                Text("بروزرسانی")
              }
            }
            if (uiState.availableVoices.isEmpty()) {
              Text(
                text = "صدای انتخابی مجزا توسط سیستم گزارش نشد؛ برنامه از صدای پیش‌فرض انگلیسی دستگاه استفاده می‌کند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              FlowRow(
                modifier = Modifier.testTag("profile_tts_voice_card"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = prefs.preferredVoiceName == null,
                  onClick = { viewModel.setPreferredVoiceName(null) },
                  label = { Text("خودکار (پیش‌فرض)") },
                  modifier = Modifier.testTag("voice_chip_auto"),
                )
                uiState.availableVoices.take(8).forEachIndexed { idx, voice ->
                  val modeBadge = if (voice.isNetworkRequired) "آنلاین" else "آفلاین"
                  FilterChip(
                    selected = prefs.preferredVoiceName == voice.name,
                    onClick = { viewModel.setPreferredVoiceName(voice.name) },
                    label = { Text("${voice.localeTag} • $modeBadge #${idx + 1}") },
                    modifier = Modifier.testTag("voice_chip_$idx"),
                  )
                }
              }
            }
          }
        }
      }

      // Daily Goal & New Cards per Day
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_daily_goal_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_daily_goal_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = { viewModel.setDailyGoalCards((prefs.dailyGoalCards - 5).coerceAtLeast(5)) },
                modifier = Modifier.testTag("goal_decrease_button"),
              ) {
                Text("۵-")
              }
              Text(
                text = "${prefs.dailyGoalCards} کارت در روز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              OutlinedButton(
                onClick = { viewModel.setDailyGoalCards((prefs.dailyGoalCards + 5).coerceAtMost(100)) },
                modifier = Modifier.testTag("goal_increase_button"),
              ) {
                Text("۵+")
              }
            }

            HorizontalDivider()
            Text(
              text = "حداکثر کارت جدید در هر جلسه مرور",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedButton(
                onClick = { viewModel.setNewCardsPerDay((prefs.newCardsPerDay - 5).coerceAtLeast(1)) },
                modifier = Modifier.testTag("new_cards_decrease_button"),
              ) {
                Text("۵-")
              }
              Text(
                text = "${prefs.newCardsPerDay} کارت جدید",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("new_cards_value_text"),
              )
              OutlinedButton(
                onClick = { viewModel.setNewCardsPerDay((prefs.newCardsPerDay + 5).coerceAtMost(50)) },
                modifier = Modifier.testTag("new_cards_increase_button"),
              ) {
                Text("۵+")
              }
            }
          }
        }
      }

      // Review & Reader Display Preferences
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_reader_prefs_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_reader_prefs_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            PreferenceToggleRow(
              title = "نمایش ترجمه فارسی در خواننده",
              checked = prefs.showTranslationsInReader,
              onCheckedChange = viewModel::setShowTranslationsInReader,
              tag = "pref_show_translations",
            )
            PreferenceToggleRow(
              title = "نمایش تلفظ آوایی (IPA) در برگه واژه",
              checked = prefs.showIpaInReader,
              onCheckedChange = viewModel::setShowIpaInReader,
              tag = "pref_show_ipa",
            )
            PreferenceToggleRow(
              title = "نمایش نکات دستوری در خواننده",
              checked = prefs.showGrammarNotesInReader,
              onCheckedChange = viewModel::setShowGrammarNotesInReader,
              tag = "pref_show_grammar_notes",
            )
            PreferenceToggleRow(
              title = "پخش خودکار تلفظ هنگام نمایش کارت جدید SRS",
              checked = prefs.autoPlaySrsAudio,
              onCheckedChange = viewModel::setAutoPlaySrsAudio,
              tag = "pref_autoplay_srs_audio",
            )
          }
        }
      }

      // Data Management
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_data_management_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_data_management_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            OutlinedButton(
              onClick = viewModel::reloadSamplePackages,
              modifier = Modifier.fillMaxWidth().testTag("data_reload_samples_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text(stringResource(R.string.learn_load_sample_button))
            }
            OutlinedButton(
              onClick = { confirmationDialog = DataResetConfirmation.RESET_PROGRESS },
              modifier = Modifier.fillMaxWidth().testTag("data_reset_progress_button"),
            ) {
              Icon(imageVector = Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text("بازنشانی پیشرفت مطالعه و نشانک‌ها")
            }
            OutlinedButton(
              onClick = { confirmationDialog = DataResetConfirmation.CLEAR_ALL_LEARNING },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.fillMaxWidth().testTag("data_clear_all_button"),
            ) {
              Icon(imageVector = Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text("پاک‌سازی کامل داده‌های یادگیری (SRS و واژه‌نامه شخصی)")
            }
          }
        }
      }

      // Optional Isolated AI Settings (Strictly Disabled by Default)
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_ai_settings_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_ai_optional_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "بام‌لینگویج تمام قابلیت‌های خواندن، واژه‌نامه، تلفظ و SRS را بدون هوش مصنوعی ارائه می‌دهد. این بخش به صورت پیش‌فرض خاموش است و هرگز کلید یا درخواست خودکاری ارسال نمی‌کند.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PreferenceToggleRow(
              title = "فعال‌سازی حالت اختیاری هوش مصنوعی",
              checked = prefs.aiEnabled,
              onCheckedChange = { enabled ->
                val targetProvider =
                  if (enabled && prefs.aiProviderType == AiProviderType.NONE) AiProviderType.CUSTOM_ENDPOINT
                  else if (!enabled) AiProviderType.NONE
                  else prefs.aiProviderType
                viewModel.setAiConfiguration(enabled, targetProvider, endpointDraft)
              },
              tag = "ai_enabled_switch",
            )
            val availabilityLabel =
              when (val avail = uiState.aiAvailability) {
                AiAvailability.DisabledByUser -> "وضعیت: خاموش (حالت تمام‌آفلاین)"
                AiAvailability.NotConfigured -> "وضعیت: نشانی سرویس‌دهنده پیکربندی نشده است"
                is AiAvailability.Ready -> "وضعیت: آماده (${avail.providerName})"
                is AiAvailability.OfflineUnavailable -> "وضعیت: ${avail.reasonFa}"
              }
            Text(
              text = availabilityLabel,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            if (prefs.aiEnabled) {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProviderType.entries.forEach { providerType ->
                  FilterChip(
                    selected = prefs.aiProviderType == providerType,
                    onClick = { viewModel.setAiConfiguration(prefs.aiEnabled, providerType, endpointDraft) },
                    label = { Text(providerType.persianLabel) },
                  )
                }
              }
              OutlinedTextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it },
                label = { Text("نشانی سرویس‌دهنده اختیاری (HTTPS)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ai_endpoint_input"),
              )
              OutlinedButton(
                onClick = {
                  viewModel.setAiConfiguration(prefs.aiEnabled, prefs.aiProviderType, endpointDraft)
                },
                modifier = Modifier.testTag("ai_save_config_button"),
              ) {
                Text("ذخیره پیکربندی اختیاری")
              }
            }
          }
        }
      }

      // App Info & Offline Privacy Guarantee
      item {
        Card(
          modifier = Modifier.fillMaxWidth().testTag("profile_app_info_card"),
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = stringResource(R.string.profile_diagnostics_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            EnglishTextBlock(
              text = "BamLinguage v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.APPLICATION_ID}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "نسخه طرحواره JSON: 1 • پایگاه داده: Room v2 (۱۲ جدول + مهاجرت ۱→۲)",
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "ذخیره‌سازی کاملاً محلی روی دستگاه بدون ردیابی، حساب کاربری اجباری یا نیاز به VPN.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }
        }
      }
    }
  }

  when (confirmationDialog) {
    DataResetConfirmation.NONE -> Unit
    DataResetConfirmation.RESET_PROGRESS -> {
      AlertDialog(
        onDismissRequest = { confirmationDialog = DataResetConfirmation.NONE },
        title = { Text("بازنشانی پیشرفت مطالعه") },
        text = { Text("تمام نشانک‌ها و پیشرفت مطالعه جملات پاک می‌شوند، اما کارت‌های مرور (SRS) حفظ خواهند شد.") },
        confirmButton = {
          Button(
            onClick = {
              confirmationDialog = DataResetConfirmation.NONE
              viewModel.resetReadingProgressAndBookmarks()
            },
            modifier = Modifier.testTag("confirm_reset_progress_button"),
          ) {
            Text("تأیید")
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmationDialog = DataResetConfirmation.NONE }) {
            Text("انصراف")
          }
        },
      )
    }
    DataResetConfirmation.CLEAR_ALL_LEARNING -> {
      AlertDialog(
        onDismissRequest = { confirmationDialog = DataResetConfirmation.NONE },
        title = { Text("پاک‌سازی کامل داده‌های یادگیری") },
        text = { Text("تمام کارت‌های SRS، تاریخچه مرور، واژه‌نامه شخصی، پیشرفت و نشانک‌ها به طور کامل حذف می‌شوند.") },
        confirmButton = {
          Button(
            onClick = {
              confirmationDialog = DataResetConfirmation.NONE
              viewModel.clearAllLearningData()
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
            modifier = Modifier.testTag("confirm_clear_all_button"),
          ) {
            Text("پاک‌سازی کامل")
          }
        },
        dismissButton = {
          TextButton(onClick = { confirmationDialog = DataResetConfirmation.NONE }) {
            Text("انصراف")
          }
        },
      )
    }
  }
}

@Composable
private fun PreferenceToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  tag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.weight(1f).padding(end = 8.dp),
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(tag),
    )
  }
}
