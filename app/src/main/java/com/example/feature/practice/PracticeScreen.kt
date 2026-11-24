package com.example.feature.practice

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.tts.TtsStatus
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.ReviewQuality
import com.example.domain.model.SrsCard
import com.example.domain.model.SrsCardType
import com.example.domain.srs.SrsScheduler
import com.example.feature.components.EnglishTextBlock
import com.example.feature.components.MetricStatCard
import com.example.ui.theme.EnglishFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
  viewModel: PracticeViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val session = uiState.session

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (session != null) {
      ReviewSessionView(
        session = session,
        intervalPreviews = uiState.intervalPreviews,
        autoPlayAudio = uiState.preferences.autoPlaySrsAudio,
        onRevealAnswer = viewModel::revealAnswer,
        onSubmitRating = viewModel::submitReview,
        onSpeak = viewModel::speakText,
        onClose = viewModel::closeSession,
      )
    } else {
      LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = stringResource(R.string.practice_title),
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "مرور هوشمند واژه‌ها و جملات با الگوریتم SM-2 و تلفظ آفلاین",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        uiState.feedbackMessage?.let { msg ->
          item {
            Surface(
              modifier = Modifier.fillMaxWidth().testTag("practice_feedback_banner"),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.tertiaryContainer,
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(text = msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::dismissFeedback, modifier = Modifier.size(28.dp)) {
                  Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن")
                }
              }
            }
          }
        }

        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            MetricStatCard(
              title = "سررسید امروز",
              value = uiState.dueQueue.size.toString(),
              icon = Icons.Rounded.Style,
              modifier = Modifier.weight(1f).testTag("srs_due_count_card"),
            )
            MetricStatCard(
              title = "کارت‌های جدید",
              value = uiState.newQueue.size.toString(),
              icon = Icons.Rounded.Style,
              modifier = Modifier.weight(1f).testTag("srs_new_count_card"),
            )
            MetricStatCard(
              title = "کل مرورها",
              value = uiState.recentLogs.size.toString(),
              icon = Icons.Rounded.History,
              modifier = Modifier.weight(1f).testTag("srs_logs_count_card"),
            )
          }
        }

        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("practice_srs_foundation_card"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "جلسه مرور روزانه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Text(
                text =
                  if (uiState.dueQueue.isEmpty() && uiState.newQueue.isEmpty())
                    "همه کارت‌های سررسید مرور شده‌اند. از صفحه خواننده می‌توانید واژه‌ها یا جملات جدید به صف بیفزایید."
                  else
                    "${uiState.dueQueue.size} کارت سررسید و ${uiState.newQueue.size} کارت جدید آماده مرور هستند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Button(
                onClick = { viewModel.startReviewSession(includeNewCards = true) },
                enabled = uiState.dueQueue.isNotEmpty() || uiState.newQueue.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("start_srs_session_button"),
              ) {
                Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("شروع جلسه مرور")
              }
            }
          }
        }

        item {
          TtsStudioCard(
            uiState = uiState,
            onInputChanged = viewModel::onTtsInputChanged,
            onSpeak = viewModel::speakSample,
            onStop = viewModel::stopSpeaking,
          )
        }

        item {
          Text(
            text = "مخزن کارت‌های من (${uiState.allCards.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PracticeDeckFilter.entries.forEach { filter ->
              FilterChip(
                selected = uiState.deckFilter == filter,
                onClick = { viewModel.setDeckFilter(filter) },
                label = { Text(filter.persianLabel) },
                modifier = Modifier.testTag("deck_filter_${filter.name.lowercase()}"),
              )
            }
          }
        }

        if (uiState.filteredDeck.isEmpty()) {
          item {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Text(
                text = "کارتی در این دسته وجود ندارد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        } else {
          items(
            items = uiState.filteredDeck,
            key = { it.cardId },
          ) { card ->
            DeckCardRow(
              card = card,
              onSpeak = { viewModel.speakText(card.frontTerm) },
              onDelete = { viewModel.deleteCard(card.cardId) },
            )
          }
        }

        if (uiState.recentLogs.isNotEmpty()) {
          item {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "تاریخچه مرورهای اخیر (${uiState.recentLogs.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
          items(
            items = uiState.recentLogs.take(10),
            key = { it.logId },
          ) { log ->
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "امتیاز کیفیت: ${log.qualityScore} • فاصله جدید: ${log.newIntervalDays} روز",
                  style = MaterialTheme.typography.bodySmall,
                )
                Text(
                  text = "ضریب آسانی ${"%.2f".format(log.newEaseFactor)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReviewSessionView(
  session: ReviewSessionState,
  intervalPreviews: Map<ReviewQuality, Int>,
  autoPlayAudio: Boolean,
  onRevealAnswer: () -> Unit,
  onSubmitRating: (ReviewQuality) -> Unit,
  onSpeak: (String) -> Unit,
  onClose: () -> Unit,
) {
  val currentCard = session.currentCard
  androidx.compose.runtime.LaunchedEffect(currentCard?.cardId, autoPlayAudio) {
    if (autoPlayAudio && !session.isCompleted && currentCard != null) {
      onSpeak(currentCard.frontTerm)
    }
  }
  val total = session.queue.size.coerceAtLeast(1)
  val progressFraction = (session.currentIndex.toFloat() / total.toFloat()).coerceIn(0f, 1f)

  Column(
    modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onClose, modifier = Modifier.testTag("srs_session_close_button")) {
        Icon(imageVector = Icons.Rounded.Close, contentDescription = null)
        Spacer(modifier = Modifier.size(4.dp))
        Text("پایان جلسه")
      }
      Text(
        text =
          if (session.isCompleted) "تکمیل شد ($total / $total)"
          else "کارت ${session.currentIndex + 1} از $total",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
    }

    LinearProgressIndicator(
      progress = { if (session.isCompleted) 1f else progressFraction },
      modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
    )

    if (session.isCompleted || session.currentCard == null) {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("srs_session_summary_card"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Text(
            text = "خلاصه جلسه مرور",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "تعداد کارت‌های مرور شده: ${session.reviewedCount}",
            style = MaterialTheme.typography.titleMedium,
          )
          ReviewQuality.entries.forEach { q ->
            val count = session.ratingCounts[q] ?: 0
            Text(
              text = "${q.persianLabel}: $count کارت",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          Button(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("srs_summary_done_button")) {
            Text("بازگشت به صفحه تمرین")
          }
        }
      }
    } else {
      val card = session.currentCard!!
      Card(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("srs_active_flashcard"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(20.dp),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = if (card.cardType == SrsCardType.WORD) "کارت واژه (${card.pos.ifBlank { "word" }})" else "کارت جمله",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
            card.sourcePackageTitle?.let { source ->
              Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            EnglishTextBlock(
              text = card.frontTerm,
              style =
                if (card.cardType == SrsCardType.WORD)
                  MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
            )
            card.phonetic?.let { ipa ->
              EnglishTextBlock(
                text = ipa,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { onSpeak(card.frontTerm) },
              modifier = Modifier.testTag("srs_card_speak_button"),
            ) {
              Icon(imageVector = Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
              Spacer(modifier = Modifier.size(6.dp))
              Text("پخش تلفظ")
            }
          }

          if (session.isAnswerRevealed) {
            Surface(
              modifier = Modifier.fillMaxWidth().testTag("srs_card_answer_panel"),
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = card.backPersianMeaning,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                card.englishDefinition?.takeIf { it.isNotBlank() }?.let { def ->
                  EnglishTextBlock(
                    text = def,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                card.contextSentenceEn?.takeIf { it.isNotBlank() && it != card.frontTerm }?.let { ctx ->
                  HorizontalDivider()
                  EnglishTextBlock(text = ctx, style = MaterialTheme.typography.bodySmall)
                  card.contextSentenceFa?.let { ctxFa ->
                    Text(
                      text = ctxFa,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          } else {
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }

      if (!session.isAnswerRevealed) {
        Button(
          onClick = onRevealAnswer,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("srs_reveal_answer_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Visibility, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text("نمایش پاسخ")
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ReviewQuality.entries.forEach { quality ->
            val days = intervalPreviews[quality] ?: 1
            val buttonColors =
              when (quality) {
                ReviewQuality.AGAIN ->
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                  )
                ReviewQuality.HARD ->
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  )
                ReviewQuality.GOOD ->
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                ReviewQuality.EASY ->
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
              }

            Button(
              onClick = { onSubmitRating(quality) },
              colors = buttonColors,
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
              modifier = Modifier.weight(1f).testTag("srs_rate_${quality.name.lowercase()}"),
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = quality.persianLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(text = "$days روز", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeckCardRow(
  card: SrsCard,
  onSpeak: () -> Unit,
  onDelete: () -> Unit,
) {
  val displayStatus = SrsScheduler.displayStatusFor(card, System.currentTimeMillis())
  Card(
    modifier = Modifier.fillMaxWidth().testTag("deck_card_${card.cardId}"),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = "${if (card.cardType == SrsCardType.WORD) "واژه" else "جمله"} • ${displayStatus.persianLabel}",
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          Text(
            text = "فاصله: ${card.intervalDays} روز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        EnglishTextBlock(
          text = card.frontTerm,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          maxLines = 2,
        )
        Text(
          text = card.backPersianMeaning,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }
      IconButton(onClick = onSpeak) {
        Icon(imageVector = Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "پخش تلفظ")
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("delete_card_${card.cardId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "حذف کارت مرور",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@Composable
private fun TtsStudioCard(
  uiState: PracticeUiState,
  onInputChanged: (String) -> Unit,
  onSpeak: () -> Unit,
  onStop: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("practice_tts_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.practice_tts_card_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      val statusLabel =
        when (val status = uiState.ttsStatus) {
          TtsStatus.Initializing -> "در حال آماده‌سازی موتور تلفظ..."
          TtsStatus.Ready ->
            "موتور آماده است (${uiState.preferences.ttsAccent.persianLabel} • ${"%.2f".format(uiState.preferences.ttsSpeed)}x)"
          is TtsStatus.Speaking -> "در حال پخش..."
          is TtsStatus.Paused -> "مکث شده"
          is TtsStatus.Unavailable -> status.reasonFa
        }
      Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        OutlinedTextField(
          value = uiState.ttsTestInput,
          onValueChange = onInputChanged,
          modifier = Modifier.fillMaxWidth().testTag("practice_tts_input"),
          textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = EnglishFontFamily),
          label = { Text("English Sentence / Word") },
          singleLine = true,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onSpeak,
          enabled = uiState.ttsStatus !is TtsStatus.Unavailable && uiState.ttsStatus !is TtsStatus.Initializing,
          modifier = Modifier.weight(1f).testTag("practice_tts_speak_button"),
        ) {
          Icon(imageVector = Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(6.dp))
          Text(stringResource(R.string.practice_tts_test_button))
        }
        OutlinedButton(
          onClick = onStop,
          enabled = uiState.ttsStatus is TtsStatus.Speaking,
          modifier = Modifier.testTag("practice_tts_stop_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text(stringResource(R.string.practice_tts_stop_button))
        }
      }
    }
  }
}
