package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.GlossaryEntry
import com.example.domain.model.GlossarySense
import com.example.domain.vocabulary.ContextualResolutionResult
import com.example.feature.components.EnglishTextBlock

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyBottomSheetContent(
  resolution: ContextualResolutionResult,
  showIpa: Boolean = true,
  aiEnabledInSettings: Boolean = false,
  optionalAiMessage: String? = null,
  onRequestOptionalAi: () -> Unit = {},
  onSpeakWord: (String) -> Unit,
  onAddSenseToSrs: (GlossaryEntry, GlossarySense?) -> Unit,
  onSaveManualWord: (term: String, meaningFa: String, definitionEn: String, pos: String, addToSrs: Boolean) -> Unit,
  onDismiss: () -> Unit = {},
) {
  val scrollState = rememberScrollState()

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .verticalScroll(scrollState)
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        EnglishTextBlock(
          text = resolution.matchedSurface,
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
        )
        val primaryEntry = resolution.primaryMatch?.entry
        if (primaryEntry != null) {
          EnglishTextBlock(
            text =
              buildString {
                append("lemma: ${primaryEntry.lemma} • ${primaryEntry.partOfSpeech} • ${primaryEntry.cefr.code}")
                if (showIpa) {
                  val uk = primaryEntry.pronunciation.uk
                  val us = primaryEntry.pronunciation.us
                  if (!us.isNullOrBlank()) append(" • US $us")
                  if (!uk.isNullOrBlank() && uk != us) append(" • UK $uk")
                }
              },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { onSpeakWord(resolution.matchedSurface) },
          modifier = Modifier.testTag("vocab_sheet_tts_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "پخش تلفظ واژه",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("vocab_sheet_close_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "بستن",
          )
        }
      }
    }

    // Contextual Sentence Box
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "جمله مرجع:",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        EnglishTextBlock(
          text = resolution.sentence.englishText,
          style = MaterialTheme.typography.bodyMedium,
        )
        Text(
          text = resolution.sentence.persianTranslation,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    val primary = resolution.primaryMatch
    if (primary != null) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              buildString {
                append(primary.matchReason)
                resolution.predictedPos?.let { append(" • نقش پیشنهادی در جمله: $it") }
              },
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }

      Text(
        text = "معانی واژه (${primary.entry.senses.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )

      primary.entry.senses.forEach { sense ->
        val isRecommended = sense.id == primary.recommendedSense?.id
        SenseCard(
          entry = primary.entry,
          sense = sense,
          isRecommended = isRecommended,
          onAddToSrs = { onAddSenseToSrs(primary.entry, sense) },
        )
      }

      if (primary.entry.synonyms.isNotEmpty() || primary.entry.antonyms.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          primary.entry.synonyms.forEach { syn ->
            AssistChip(
              onClick = {},
              label = { Text("هم‌معنی: $syn") },
            )
          }
          primary.entry.antonyms.forEach { ant ->
            AssistChip(
              onClick = {},
              label = { Text("متضاد: $ant") },
            )
          }
        }
      }

      if (resolution.alternativeMatches.isNotEmpty()) {
        HorizontalDivider()
        Text(
          text = "مدخل‌ها و نقش‌های دیگر در واژه‌نامه",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        resolution.alternativeMatches.forEach { alt ->
          val altSense = alt.recommendedSense ?: alt.entry.senses.firstOrNull()
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
              ),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              EnglishTextBlock(
                text = "${alt.entry.term} (${alt.entry.partOfSpeech}) • lemma: ${alt.entry.lemma}",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = altSense?.meaningFa ?: alt.entry.persianMeaning,
                style = MaterialTheme.typography.bodyMedium,
              )
              if (!altSense?.definitionEn.isNullOrBlank()) {
                EnglishTextBlock(
                  text = altSense.definitionEn,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              OutlinedButton(
                onClick = { onAddSenseToSrs(alt.entry, altSense) },
                modifier = Modifier.testTag("vocab_sheet_add_alt_srs"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Style,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("افزودن این معنی به SRS")
              }
            }
          }
        }
      }
    } else {
      // Missing / unknown glossary entry: Manual add fallback
      ManualVocabularyAddCard(
        initialTerm = resolution.tappedToken.surface,
        onSaveManualWord = onSaveManualWord,
      )
    }

    if (aiEnabledInSettings) {
      HorizontalDivider()
      OutlinedButton(
        onClick = onRequestOptionalAi,
        modifier = Modifier.fillMaxWidth().testTag("vocab_sheet_optional_ai_button"),
      ) {
        Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text("توضیح اختیاری هوش مصنوعی")
      }
      optionalAiMessage?.let { aiMsg ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
          Text(
            text = aiMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun SenseCard(
  entry: GlossaryEntry,
  sense: GlossarySense,
  isRecommended: Boolean,
  onAddToSrs: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isRecommended) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.surfaceContainerLow,
      ),
    border =
      if (isRecommended) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
      else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (isRecommended) {
        Text(
          text = "معنی پیشنهادی در این جمله",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = sense.meaningFa,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      if (sense.definitionEn.isNotBlank()) {
        EnglishTextBlock(
          text = sense.definitionEn,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      sense.examples.forEach { ex ->
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            EnglishTextBlock(
              text = ex.text,
              style = MaterialTheme.typography.bodySmall,
            )
            if (ex.translationFa.isNotBlank()) {
              Text(
                text = ex.translationFa,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      Button(
        onClick = onAddToSrs,
        modifier = Modifier.fillMaxWidth().testTag("vocab_sheet_add_srs_${entry.sourceGlossaryId}_${sense.sourceSenseId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Add,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text("افزودن به کارت‌های مرور (SRS)")
      }
    }
  }
}

@Composable
private fun ManualVocabularyAddCard(
  initialTerm: String,
  onSaveManualWord: (term: String, meaningFa: String, definitionEn: String, pos: String, addToSrs: Boolean) -> Unit,
) {
  var term by remember(initialTerm) { mutableStateOf(initialTerm) }
  var meaningFa by remember { mutableStateOf("") }
  var definitionEn by remember { mutableStateOf("") }
  var pos by remember { mutableStateOf("word") }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("manual_vocab_add_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "این واژه در واژه‌نامه بسته موجود نیست",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "می‌توانید به صورت دستی معنی آن را ثبت کنید و مستقیماً به واژه‌نامه شخصی و کارت‌های مرور اضافه نمایید.",
        style = MaterialTheme.typography.bodySmall,
      )
      OutlinedTextField(
        value = term,
        onValueChange = { term = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("واژه انگلیسی") },
        singleLine = true,
      )
      OutlinedTextField(
        value = meaningFa,
        onValueChange = { meaningFa = it },
        modifier = Modifier.fillMaxWidth().testTag("manual_vocab_meaning_input"),
        label = { Text("معنی فارسی") },
        singleLine = true,
      )
      OutlinedTextField(
        value = definitionEn,
        onValueChange = { definitionEn = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("توضیح انگلیسی (اختیاری)") },
        singleLine = true,
      )
      OutlinedTextField(
        value = pos,
        onValueChange = { pos = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("نقش دستوری (noun / verb / ...)") },
        singleLine = true,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onSaveManualWord(term, meaningFa, definitionEn, pos, true) },
          enabled = term.isNotBlank() && meaningFa.isNotBlank(),
          modifier = Modifier.weight(1f).testTag("manual_vocab_save_srs_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("ذخیره در SRS")
        }
        OutlinedButton(
          onClick = { onSaveManualWord(term, meaningFa, definitionEn, pos, false) },
          enabled = term.isNotBlank() && meaningFa.isNotBlank(),
          modifier = Modifier.weight(1f),
        ) {
          Icon(imageVector = Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("فقط واژه‌نامه")
        }
      }
    }
  }
}
