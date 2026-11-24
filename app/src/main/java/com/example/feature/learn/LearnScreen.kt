package com.example.feature.learn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.content.importer.JsonContentPackageImporter
import com.example.content.importer.PackageImportPreview
import com.example.content.validation.ContentIntegrityReport
import com.example.core.common.CefrLevel
import com.example.core.common.ContentType
import com.example.domain.model.ContentPackageSummary
import com.example.domain.model.LearningProgress
import com.example.feature.components.EnglishTextBlock
import com.example.feature.components.UserVocabularyBottomSheet
import java.io.InputStreamReader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnScreen(
  viewModel: LearnViewModel,
  onOpenReader: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showJsonPasteDialog by remember { mutableStateOf(false) }
  var showUserVocabularySheet by remember { mutableStateOf(false) }

  val filePickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
      if (uri != null) {
        val readResult =
          runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
              readBoundedUtf8Text(stream, JsonContentPackageImporter.MAX_JSON_CHARS)
            }
          }
        val text = readResult.getOrNull()
        if (text != null) {
          viewModel.requestImportPreview(text)
        } else {
          viewModel.reportFileReadFailure("خواندن فایل انتخاب‌شده ممکن نشد.")
        }
      }
    }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("learn_packages_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text = stringResource(R.string.learn_title),
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = stringResource(R.string.learn_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedButton(
            onClick = { showUserVocabularySheet = true },
            modifier = Modifier.testTag("learn_user_vocab_button"),
          ) {
            Icon(imageVector = Icons.Rounded.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("واژه‌های من (${uiState.allUserVocabulary.size})")
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = { showJsonPasteDialog = true },
            modifier = Modifier.weight(1f).testTag("learn_import_json_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.AddCircleOutline,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(stringResource(R.string.learn_import_json_button))
          }
          OutlinedButton(
            onClick = { filePickerLauncher.launch(arrayOf("application/json", "text/plain")) },
            modifier = Modifier.testTag("learn_pick_file_button"),
          ) {
            Icon(imageVector = Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("انتخاب فایل")
          }
        }
      }

      uiState.importFeedbackMessage?.let { feedback ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("learn_import_feedback"),
            shape = MaterialTheme.shapes.medium,
            color =
              if (uiState.isImportError) MaterialTheme.colorScheme.errorContainer
              else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor =
              if (uiState.isImportError) MaterialTheme.colorScheme.onErrorContainer
              else MaterialTheme.colorScheme.onTertiaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = viewModel::dismissFeedback,
                modifier = Modifier.size(32.dp),
              ) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن پیام")
              }
            }
          }
        }
      }

      // Offline Dictionary Lookup
      item {
        OutlinedTextField(
          value = uiState.dictionaryQuery,
          onValueChange = viewModel::onDictionaryQueryChanged,
          modifier = Modifier.fillMaxWidth().testTag("learn_dictionary_search_input"),
          label = { Text("جست‌وجوی آفلاین در واژه‌نامه (واژه، ریشه یا شکل صرف‌شده)") },
          leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (uiState.dictionaryQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.onDictionaryQueryChanged("") }) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "پاک کردن")
              }
            }
          },
          singleLine = true,
        )
      }

      if (uiState.dictionaryQuery.isNotBlank()) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("learn_dictionary_results_card"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = "نتایج واژه‌نامه (${uiState.dictionaryResults.size + uiState.savedVocabularyMatches.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              if (uiState.dictionaryResults.isEmpty() && uiState.savedVocabularyMatches.isEmpty()) {
                Text(
                  text = "واژه‌ای مطابق عبارت واردشده در واژه‌نامه‌های محلی پیدا نشد.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              } else {
                uiState.dictionaryResults.take(15).forEach { entry ->
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth().padding(10.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        EnglishTextBlock(
                          text = "${entry.term} [${entry.partOfSpeech} • ${entry.cefr.code}] ${entry.pronunciation.primary().orEmpty()}",
                          style = MaterialTheme.typography.titleSmall,
                          modifier = Modifier.weight(1f),
                        )
                        IconButton(
                          onClick = { viewModel.speakWord(entry.term) },
                          modifier = Modifier.size(36.dp),
                        ) {
                          Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "تلفظ واژه",
                            tint = MaterialTheme.colorScheme.primary,
                          )
                        }
                      }
                      Text(
                        text = entry.senses.joinToString(" | ") { it.meaningFa },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      OutlinedButton(
                        onClick = { viewModel.addDictionaryEntryToSrs(entry) },
                        modifier = Modifier.testTag("dict_add_srs_${entry.sourceGlossaryId}"),
                      ) {
                        Icon(imageVector = Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("افزودن به SRS")
                      }
                    }
                  }
                }
                uiState.savedVocabularyMatches.forEach { saved ->
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(10.dp),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        EnglishTextBlock(text = "${saved.displayTerm} (دفترچه شخصی)", style = MaterialTheme.typography.titleSmall)
                        Text(text = saved.persianMeaning, style = MaterialTheme.typography.bodySmall)
                      }
                      IconButton(onClick = { viewModel.speakWord(saved.displayTerm) }) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "تلفظ")
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Library Search + Level + Content Type + Topic Filters
      item {
        OutlinedTextField(
          value = uiState.packageSearchQuery,
          onValueChange = viewModel::onPackageSearchQueryChanged,
          modifier = Modifier.fillMaxWidth().testTag("learn_package_search_input"),
          label = { Text("جست‌وجو در عنوان، موضوع یا توضیح داستان و گفت‌وگو") },
          leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (uiState.packageSearchQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.onPackageSearchQueryChanged("") }) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "پاک کردن")
              }
            }
          },
          singleLine = true,
        )
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item {
              FilterChip(
                selected = uiState.selectedLevel == null,
                onClick = { viewModel.selectLevel(null) },
                label = { Text(stringResource(R.string.learn_filter_all)) },
                modifier = Modifier.testTag("filter_level_all"),
              )
            }
            items(CefrLevel.entries) { level ->
              FilterChip(
                selected = uiState.selectedLevel == level,
                onClick = { viewModel.selectLevel(level) },
                label = { Text(level.code) },
                modifier = Modifier.testTag("filter_level_${level.code}"),
              )
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FilterChip(
              selected = uiState.selectedType == null,
              onClick = { viewModel.selectType(null) },
              label = { Text("همه نوع‌ها") },
              modifier = Modifier.testTag("filter_type_all"),
            )
            ContentType.entries.forEach { contentType ->
              FilterChip(
                selected = uiState.selectedType == contentType,
                onClick = { viewModel.selectType(contentType) },
                label = { Text(contentType.persianLabel) },
                modifier = Modifier.testTag("filter_type_${contentType.wireValue}"),
              )
            }
            uiState.availableTopics.forEach { topic ->
              FilterChip(
                selected = uiState.selectedTopic.equals(topic, ignoreCase = true),
                onClick = { viewModel.selectTopic(topic) },
                label = { Text("# $topic") },
                modifier = Modifier.testTag("filter_topic_$topic"),
              )
            }
          }
        }
      }

      if (uiState.packages.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth().testTag("learn_empty_state_card"),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              ),
          ) {
            Column(
              modifier = Modifier.padding(20.dp).fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.learn_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = stringResource(R.string.learn_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (uiState.totalLibraryPackageCount > 0) {
                OutlinedButton(
                  onClick = viewModel::clearPackageFilters,
                  modifier = Modifier.testTag("learn_clear_filters_button"),
                ) {
                  Text("پاک کردن فیلترها")
                }
              } else {
                OutlinedButton(
                  onClick = viewModel::reloadBundledSample,
                  modifier = Modifier.testTag("learn_reload_sample_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(stringResource(R.string.learn_load_sample_button))
                }
              }
            }
          }
        }
      } else {
        items(
          items = uiState.packages,
          key = { it.contentPackage.packageId },
        ) { summary ->
          PackageLibraryCard(
            summary = summary,
            progress = uiState.progressByPackageId[summary.contentPackage.packageId],
            onOpenReader = { onOpenReader(summary.contentPackage.packageId) },
            onInspect = { viewModel.inspectPackage(summary.contentPackage.packageId) },
            onRequestDelete = { viewModel.requestDeletePackage(summary) },
          )
        }
      }
    }
  }

  if (showJsonPasteDialog) {
    JsonImportDialog(
      onDismiss = { showJsonPasteDialog = false },
      onConfirmImport = { raw ->
        showJsonPasteDialog = false
        viewModel.requestImportPreview(raw)
      },
    )
  }

  uiState.pendingImportPreview?.let { preview ->
    PackageImportPreviewDialog(
      preview = preview,
      onDismiss = viewModel::dismissImportPreview,
      onConfirmImport = viewModel::confirmPreviewImport,
    )
  }

  uiState.inspectedPackage?.let { inspected ->
    PackageInspectorDialog(
      details = inspected,
      onDismiss = viewModel::dismissInspector,
      onOpenReader = {
        viewModel.dismissInspector()
        onOpenReader(inspected.bundle.contentPackage.packageId)
      },
      onRequestDelete = {
        val summary = uiState.packages.firstOrNull {
          it.contentPackage.packageId == inspected.bundle.contentPackage.packageId
        }
        if (summary != null) {
          viewModel.requestDeletePackage(summary)
        }
      },
    )
  }

  uiState.pendingDeletePackage?.let { target ->
    AlertDialog(
      onDismissRequest = viewModel::dismissDeleteConfirmation,
      title = { Text("حذف بسته محتوا") },
      text = {
        Text(
          "آیا بسته «${target.contentPackage.title}» حذف شود؟\nکارت‌های مرور (SRS) و تاریخچه یادگیری شما به صورت مستقل حفظ خواهند شد.",
        )
      },
      confirmButton = {
        Button(
          onClick = viewModel::confirmDeletePackage,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_delete_package_button"),
        ) {
          Text("حذف بسته")
        }
      },
      dismissButton = {
        TextButton(onClick = viewModel::dismissDeleteConfirmation) {
          Text("انصراف")
        }
      },
    )
  }

  if (showUserVocabularySheet) {
    UserVocabularyBottomSheet(
      items = uiState.allUserVocabulary,
      onSpeakWord = viewModel::speakWord,
      onPromoteToSrs = viewModel::promoteUserVocabularyToSrs,
      onDeleteItem = viewModel::deleteUserVocabularyItem,
      onDismiss = { showUserVocabularySheet = false },
    )
  }
}

private fun readBoundedUtf8Text(
  stream: java.io.InputStream,
  maxChars: Int,
): String {
  val reader = InputStreamReader(stream, Charsets.UTF_8)
  val sb = StringBuilder()
  val buffer = CharArray(8192)
  val cap = maxChars + 1
  while (sb.length < cap) {
    val read = reader.read(buffer, 0, minOf(buffer.size, cap - sb.length))
    if (read <= 0) break
    sb.append(buffer, 0, read)
  }
  return sb.toString()
}

@Composable
private fun PackageLibraryCard(
  summary: ContentPackageSummary,
  progress: LearningProgress?,
  onOpenReader: () -> Unit,
  onInspect: () -> Unit,
  onRequestDelete: () -> Unit,
) {
  val pkg = summary.contentPackage
  val completedSentences = (progress?.completedSentenceCount ?: 0).coerceAtMost(summary.sentenceCount)
  val totalSentences = summary.sentenceCount.coerceAtLeast(1)
  val fraction = (completedSentences.toFloat() / totalSentences.toFloat()).coerceIn(0f, 1f)

  Card(
    onClick = onOpenReader,
    modifier = Modifier.fillMaxWidth().testTag("package_card_${pkg.packageId}"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
          Text(
            text = "${pkg.level.code} • ${pkg.contentType.persianLabel} • ${pkg.topic}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
        Text(
          text = "v${pkg.contentVersion} • ${pkg.metadata.estimatedMinutes} دقیقه",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      EnglishTextBlock(
        text = pkg.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = pkg.titleFa,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = pkg.descriptionFa,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "${summary.sentenceCount} جمله • ${summary.glossaryCount} واژه • ${(fraction * 100).toInt()}٪",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.tertiary,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "مطالعه در خواننده",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onInspect,
          modifier = Modifier.weight(1f).testTag("package_inspect_${pkg.packageId}"),
        ) {
          Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.size(4.dp))
          Text("جزئیات و سلامت")
        }
        IconButton(
          onClick = onRequestDelete,
          modifier = Modifier.testTag("package_delete_${pkg.packageId}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "حذف بسته",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun PackageImportPreviewDialog(
  preview: PackageImportPreview,
  onDismiss: () -> Unit,
  onConfirmImport: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("import_preview_dialog"),
    title = { Text("پیش‌نمایش و گزارش سلامت بسته") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when (preview) {
          is PackageImportPreview.ParseFailed -> {
            Text(
              text = "ورودی قابل پردازش نیست:",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold,
            )
            preview.errors.forEach { err ->
              Text(
                text = "• ${err.fieldPath}: ${err.messageFa}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
          is PackageImportPreview.Ready -> {
            val report = preview.integrityReport
            EnglishTextBlock(
              text = "${preview.dto.title} (${preview.dto.id})",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text =
                buildString {
                  append("نسخه جدید: ${preview.dto.version}")
                  if (preview.existingVersion != null) {
                    append(" • نسخه نصب‌شده فعلی: ${preview.existingVersion}")
                    if (preview.isUpgrade) append(" (به‌روزرسانی امن با حفظ SRS)")
                  }
                },
              style = MaterialTheme.typography.bodySmall,
            )
            Text(
              text = "${report.sentenceCount} جمله • ${report.glossaryCount} مدخل واژه‌نامه • ${report.questionCount} پرسش",
              style = MaterialTheme.typography.labelMedium,
            )
            IntegritySummaryContent(report = report)
            preview.versionConflictError?.let { conflict ->
              HorizontalDivider()
              Text(
                text = "• ${conflict.messageFa}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      if (preview is PackageImportPreview.Ready && preview.canImport) {
        Button(
          onClick = onConfirmImport,
          modifier = Modifier.testTag("import_preview_confirm_button"),
        ) {
          Text(if (preview.isUpgrade) "تأیید و ارتقای نسخه" else "تأیید و وارد کردن")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("بستن")
      }
    },
  )
}

@Composable
private fun PackageInspectorDialog(
  details: InspectedPackageDetails,
  onDismiss: () -> Unit,
  onOpenReader: () -> Unit,
  onRequestDelete: () -> Unit,
) {
  val pkg = details.bundle.contentPackage
  val report = details.integrityReport
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("package_inspector_dialog"),
    title = { Text("مشخصات و گزارش یکپارچگی بسته") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        EnglishTextBlock(
          text = pkg.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "شناسه: ${pkg.packageId} • نسخه: ${pkg.contentVersion} • سطح: ${pkg.level.code}",
          style = MaterialTheme.typography.bodySmall,
        )
        Text(
          text = "نویسنده: ${pkg.metadata.author} • موضوع: ${pkg.topic} • زبان: ${pkg.metadata.language}→${pkg.metadata.translationLanguage}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "${details.bundle.sentences.size} جمله • ${details.bundle.glossaryEntries.size} مدخل واژه‌نامه • ${details.bundle.speakers.size} گوینده • ${details.bundle.questions.size} پرسش",
          style = MaterialTheme.typography.labelMedium,
        )
        HorizontalDivider()
        IntegritySummaryContent(report = report)
      }
    },
    confirmButton = {
      Button(
        onClick = onOpenReader,
        modifier = Modifier.testTag("inspector_open_reader_button"),
      ) {
        Text("باز کردن خواننده")
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
          onClick = onRequestDelete,
          modifier = Modifier.testTag("inspector_delete_button"),
        ) {
          Text("حذف بسته", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDismiss) {
          Text("بستن")
        }
      }
    },
  )
}

@Composable
private fun IntegritySummaryContent(report: ContentIntegrityReport) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = if (report.hasCompleteDictionaryCoverage) Icons.Rounded.Verified else Icons.Rounded.WarningAmber,
      contentDescription = null,
      tint =
        if (report.fatalErrors.isNotEmpty()) MaterialTheme.colorScheme.error
        else if (report.hasCompleteDictionaryCoverage) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = "پوشش واژه‌نامه: ${report.coveragePercent}٪ (${report.coveredTokens} از ${report.totalMeaningfulTokens} توکن)",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
    )
  }

  if (report.fatalErrors.isNotEmpty()) {
    Text(
      text = "خطاهای بحرانی ساختار (${report.fatalErrors.size}):",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.error,
      fontWeight = FontWeight.Bold,
    )
    report.fatalErrors.take(5).forEach { err ->
      Text(
        text = "• ${err.fieldPath}: ${err.messageFa}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }

  if (report.missingVocabularyWarnings.isNotEmpty()) {
    Text(
      text = "هشدارهای پوشش واژگان (${report.missingVocabularyWarnings.size}):",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    report.missingVocabularyWarnings.forEach { warn ->
      Text(text = "• ${warn.messageFa}", style = MaterialTheme.typography.bodySmall)
    }
  }

  if (report.linguisticWarnings.isNotEmpty()) {
    Text(
      text = "هشدارهای کیفیت زبان‌شناختی (${report.linguisticWarnings.size}):",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Bold,
    )
    report.linguisticWarnings.take(5).forEach { warn ->
      Text(
        text = "• ${warn.messageFa}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (report.hasCompleteDictionaryCoverage && report.linguisticWarnings.isEmpty()) {
    Text(
      text = "تمام بررسی‌های یکپارچگی (شناسه‌های یکتا، پوشش کامل واژگان، ریشه‌یابی و بازه‌های متنی) با موفقیت تأیید شدند.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.tertiary,
    )
  }
}

@Composable
private fun JsonImportDialog(
  onDismiss: () -> Unit,
  onConfirmImport: (String) -> Unit,
) {
  var jsonInput by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("وارد کردن بسته JSON (نسخه ۱)") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "متن JSON بسته محتوا را جای‌گذاری کنید تا ساختار، پوشش واژه‌نامه و بازه‌های UTF-16 آن پیش‌نمایش و بررسی شوند.",
          style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
          value = jsonInput,
          onValueChange = { jsonInput = it },
          modifier = Modifier.fillMaxWidth().height(180.dp).testTag("json_import_text_field"),
          placeholder = { Text("{\"schemaVersion\": 1, ...}") },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmImport(jsonInput) },
        enabled = jsonInput.isNotBlank(),
        modifier = Modifier.testTag("confirm_json_import_button"),
      ) {
        Text("بررسی و پیش‌نمایش")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("انصراف")
      }
    },
  )
}
