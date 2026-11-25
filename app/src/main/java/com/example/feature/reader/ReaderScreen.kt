package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.TtsAccent
import com.example.core.tts.TtsStatus
import com.example.domain.model.ComprehensionQuestion
import com.example.domain.model.ReaderWordStatus
import com.example.domain.model.Sentence
import com.example.domain.model.Speaker
import com.example.feature.components.EnglishTextBlock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snapshot = uiState.snapshot

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    when {
      uiState.isLoading -> {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      }
      snapshot == null -> {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("بسته محتوای مورد نظر یافت نشد.")
          Button(onClick = onNavigateBack) { Text("بازگشت") }
        }
      }
      else -> {
        val bundle = snapshot.bundle
        val pkg = bundle.contentPackage
        val speakersById = bundle.speakers.associateBy { it.id }
        val validSentenceIds = bundle.sentences.map { it.id }.toSet()
        val completedSentenceIds = snapshot.progress?.completedSentenceIds.orEmpty().intersect(validSentenceIds)
        val completedCount =
          if (completedSentenceIds.isNotEmpty() || snapshot.progress == null) completedSentenceIds.size
          else snapshot.progress.completedSentenceCount.coerceAtMost(bundle.sentences.size)
        val totalSentences = bundle.sentences.size.coerceAtLeast(1)
        val completionFraction = (completedCount.toFloat() / totalSentences.toFloat()).coerceIn(0f, 1f)

        LazyColumn(
          modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("reader_lazy_column"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("reader_back_button"),
              ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.size(4.dp))
                Text("کتابخانه")
              }
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ) {
                Text(
                  text = "${pkg.level.code} • ${pkg.contentType.persianLabel}",
                  style = MaterialTheme.typography.labelMedium,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }

          item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              EnglishTextBlock(
                text = pkg.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = pkg.titleFa,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "پیشرفت مطالعه: $completedCount از ${bundle.sentences.size} جمله (${(completionFraction * 100).toInt()}٪)",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.tertiary,
                )
                FilterChip(
                  selected = uiState.preferences.showTranslationsInReader,
                  onClick = viewModel::toggleTranslationsVisibility,
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.Translate,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  label = { Text("ترجمه فارسی") },
                  modifier = Modifier.testTag("reader_toggle_translation_chip"),
                )
              }
              LinearProgressIndicator(
                progress = { completionFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
              )
            }
          }

          item {
            ReaderAudioControlsCard(
              ttsStatus = uiState.ttsStatus,
              currentAccent = uiState.preferences.ttsAccent,
              ttsSpeed = uiState.preferences.ttsSpeed,
              speakers = bundle.speakers,
              speakerVoiceMap = uiState.speakerVoiceMap,
              availableVoiceCount = uiState.availableVoices.size,
              onPlayAll = viewModel::speakAllParagraphs,
              onPause = viewModel::pauseTts,
              onResume = viewModel::resumeTts,
              onStop = viewModel::stopTts,
              onSelectAccent = viewModel::setTtsAccent,
            )
          }

          item {
            SrsColorLegendRow()
          }

          uiState.feedbackBanner?.let { banner ->
            item {
              Surface(
                modifier = Modifier.fillMaxWidth().testTag("reader_feedback_banner"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = banner,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                  )
                  IconButton(onClick = viewModel::dismissFeedback, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن پیام")
                  }
                }
              }
            }
          }

          items(
            items = bundle.sentences,
            key = { "sentence_${it.id}" },
          ) { sentence ->
            val speaker = sentence.speakerId?.let { speakersById[it] }
            val isSpeakingThis =
              (uiState.ttsStatus as? TtsStatus.Speaking)?.activeItemId == sentence.id
            val isBookmarked = snapshot.bookmarksBySentenceId.containsKey(sentence.id)
            val isRead = sentence.id in completedSentenceIds
            val hasSentenceCard = snapshot.sentenceSrsCardsBySentenceId.containsKey(sentence.id)

            ReaderSentenceCard(
              sentence = sentence,
              speaker = speaker,
              highlights = snapshot.tokenHighlightsBySentenceId[sentence.id].orEmpty(),
              showTranslation = uiState.preferences.showTranslationsInReader,
              showGrammarNotes = uiState.preferences.showGrammarNotesInReader,
              isSpeakingThis = isSpeakingThis,
              isBookmarked = isBookmarked,
              isRead = isRead,
              hasSentenceCard = hasSentenceCard,
              onWordTapped = { offset -> viewModel.onWordTapped(sentence, offset) },
              onSpeakSentence = { viewModel.speakSentence(sentence) },
              onToggleBookmark = { viewModel.toggleBookmark(sentence) },
              onMarkRead = { viewModel.markSentenceRead(sentence) },
              onAddSentenceToSrs = { viewModel.addSentenceToSrs(sentence) },
            )
          }

          if (bundle.questions.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "پرسش‌های درک مطلب (${bundle.questions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            items(
              items = bundle.questions,
              key = { "question_${it.id}" },
            ) { question ->
              ComprehensionQuestionCard(
                question = question,
                selectedOption = uiState.answeredQuestions[question.id],
                onSelectOption = { optIndex -> viewModel.answerQuestion(question.id, optIndex) },
              )
            }
          }
        }
      }
    }
  }

  uiState.activeResolution?.let { resolution ->
    ModalBottomSheet(
      onDismissRequest = viewModel::dismissVocabularySheet,
      modifier = Modifier.testTag("reader_vocabulary_bottom_sheet"),
    ) {
      VocabularyBottomSheetContent(
        resolution = resolution,
        showIpa = uiState.preferences.showIpaInReader,
        aiEnabledInSettings = uiState.preferences.aiEnabled,
        optionalAiMessage = uiState.optionalAiMessage,
        onRequestOptionalAi = {
          viewModel.requestOptionalAiExplanation(
            term = resolution.matchedSurface,
            sentenceText = resolution.sentence.englishText,
          )
        },
        onSpeakWord = viewModel::speakWord,
        onAddSenseToSrs = viewModel::addWordSenseToSrs,
        onSaveManualWord = viewModel::saveManualWord,
        onDismiss = viewModel::dismissVocabularySheet,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderAudioControlsCard(
  ttsStatus: TtsStatus,
  currentAccent: TtsAccent,
  ttsSpeed: Float,
  speakers: List<Speaker>,
  speakerVoiceMap: Map<String, String>,
  availableVoiceCount: Int,
  onPlayAll: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onStop: () -> Unit,
  onSelectAccent: (TtsAccent) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("reader_audio_controls_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "پخش صوتی متن (سرعت ${"%.2f".format(ttsSpeed)}x)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        if (ttsStatus is TtsStatus.Unavailable) {
          Text(
            text = "بدون صوت سیستم",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }

      if (ttsStatus is TtsStatus.Unavailable) {
        Text(
          text = ttsStatus.reasonFa,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when (ttsStatus) {
          is TtsStatus.Speaking -> {
            Button(
              onClick = onPause,
              modifier = Modifier.weight(1f).testTag("reader_tts_pause_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(4.dp))
              Text("مکث")
            }
            OutlinedButton(
              onClick = onStop,
              modifier = Modifier.testTag("reader_tts_stop_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(4.dp))
              Text("توقف")
            }
          }
          is TtsStatus.Paused -> {
            Button(
              onClick = onResume,
              modifier = Modifier.weight(1f).testTag("reader_tts_resume_button"),
            ) {
              Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(4.dp))
              Text("ادامه (${ttsStatus.remainingCount})")
            }
            OutlinedButton(onClick = onStop) {
              Text("توقف")
            }
          }
          else -> {
            Button(
              onClick = onPlayAll,
              enabled = ttsStatus !is TtsStatus.Unavailable && ttsStatus !is TtsStatus.Initializing,
              modifier = Modifier.weight(1f).testTag("reader_tts_play_all_button"),
            ) {
              Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.size(6.dp))
              Text("پخش کامل متن")
            }
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TtsAccent.entries.forEach { accent ->
          FilterChip(
            selected = currentAccent == accent,
            onClick = { onSelectAccent(accent) },
            label = { Text(accent.persianLabel) },
          )
        }
      }

      if (speakers.isNotEmpty()) {
        Text(
          text =
            if (availableVoiceCount > 1)
              "گویندگان گفت‌وگو (${speakers.size}) به صداهای موجود دستگاه متصل شدند."
            else
              "گویندگان گفت‌وگو با صدای پیش‌فرض انگلیسی دستگاه پخش می‌شوند.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SrsColorLegendRow() {
  val palette = rememberReaderSrsPalette()
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("reader_srs_legend"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    LegendBadge(label = "جدید", bg = palette.newBg, fg = palette.newText, symbol = "○")
    LegendBadge(label = "در حال یادگیری", bg = palette.learningBg, fg = palette.learningText, symbol = "◐")
    LegendBadge(label = "مرور شده", bg = palette.reviewBg, fg = palette.reviewText, symbol = "●")
    LegendBadge(label = "سررسید مرور", bg = palette.dueBg, fg = palette.dueText, symbol = "!")
  }
}

@Composable
private fun LegendBadge(
  label: String,
  bg: Color,
  fg: Color,
  symbol: String,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = bg,
    contentColor = fg,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(text = symbol, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
      Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun ReaderSentenceCard(
  sentence: Sentence,
  speaker: Speaker?,
  highlights: List<com.example.domain.usecase.ReaderTokenHighlight>,
  showTranslation: Boolean,
  showGrammarNotes: Boolean,
  isSpeakingThis: Boolean,
  isBookmarked: Boolean,
  isRead: Boolean,
  hasSentenceCard: Boolean,
  onWordTapped: (Int) -> Unit,
  onSpeakSentence: () -> Unit,
  onToggleBookmark: () -> Unit,
  onMarkRead: () -> Unit,
  onAddSentenceToSrs: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("reader_sentence_card_${sentence.orderIndex}"),
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSpeakingThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.surfaceContainerLow,
      ),
    border =
      BorderStroke(
        width = if (isSpeakingThis) 2.dp else 1.dp,
        color =
          if (isSpeakingThis) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      ),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier =
              Modifier.size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${sentence.orderIndex + 1}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
          if (speaker != null) {
            Icon(
              imageVector = Icons.Rounded.RecordVoiceOver,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = "${speaker.nameFa} (${speaker.name})",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onSpeakSentence,
            modifier = Modifier.testTag("sentence_tts_button_${sentence.orderIndex}"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
              contentDescription = "پخش جمله",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onToggleBookmark,
            modifier = Modifier.testTag("sentence_bookmark_button_${sentence.orderIndex}"),
          ) {
            Icon(
              imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
              contentDescription = "نشانک‌گذاری جمله",
              tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      ColorCodedSentenceText(
        sentenceText = sentence.englishText,
        highlights = highlights,
        isCurrentlyPlaying = isSpeakingThis,
        onOffsetTapped = onWordTapped,
        modifier = Modifier.fillMaxWidth().testTag("sentence_text_${sentence.orderIndex}"),
      )

      if (showTranslation) {
        Text(
          text = sentence.persianTranslation,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (showGrammarNotes) {
        sentence.grammarNoteFa?.let { note ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Text(
              text = "نکته: $note",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onMarkRead,
          modifier = Modifier.weight(1f).testTag("sentence_mark_read_${sentence.orderIndex}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isRead) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(if (isRead) "خوانده شد" else "ثبت مطالعه")
        }

        OutlinedButton(
          onClick = onAddSentenceToSrs,
          modifier = Modifier.weight(1f).testTag("sentence_add_srs_${sentence.orderIndex}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Style,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(4.dp))
          Text(if (hasSentenceCard) "در SRS ثبت شده" else "افزودن جمله به SRS")
        }
      }
    }
  }
}

@Composable
private fun ComprehensionQuestionCard(
  question: ComprehensionQuestion,
  selectedOption: Int?,
  onSelectOption: (Int) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("reader_question_${question.orderIndex}"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EnglishTextBlock(
        text = "${question.orderIndex + 1}. ${question.promptEn}",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
      )
      Text(
        text = question.promptFa,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      question.options.forEachIndexed { idx, opt ->
        val isSelected = selectedOption == idx
        val isCorrect = idx == question.correctOptionIndex
        val containerColor =
          when {
            selectedOption == null -> MaterialTheme.colorScheme.surfaceContainer
            isSelected && isCorrect -> MaterialTheme.colorScheme.tertiaryContainer
            isSelected && !isCorrect -> MaterialTheme.colorScheme.errorContainer
            isCorrect -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceContainer
          }

        Surface(
          onClick = { onSelectOption(idx) },
          shape = MaterialTheme.shapes.small,
          color = containerColor,
          modifier = Modifier.fillMaxWidth().testTag("question_${question.orderIndex}_option_$idx"),
        ) {
          EnglishTextBlock(
            text = opt,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      if (selectedOption != null && !question.explanationFa.isNullOrBlank()) {
        Text(
          text = question.explanationFa,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}
