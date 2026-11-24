package com.example.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.srs.DailyActivityPoint
import com.example.feature.components.EnglishTextBlock
import com.example.feature.components.MetricStatCard

@Composable
fun ProgressScreen(
  viewModel: ProgressViewModel,
  onOpenReader: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val stats = uiState.stats
  val analytics = uiState.analytics

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("progress_lazy_column"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.progress_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = stringResource(R.string.progress_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Row 1: Streak & Review Accuracy
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricStatCard(
            title = "پیوستگی یادگیری",
            value = "${analytics.streakDays} روز",
            icon = Icons.Rounded.LocalFireDepartment,
            modifier = Modifier.weight(1f).testTag("progress_streak_metric"),
          )
          MetricStatCard(
            title = "دقت مرورها",
            value = analytics.reviewAccuracyPercent?.let { "$it٪" } ?: "—",
            icon = Icons.Rounded.Percent,
            modifier = Modifier.weight(1f).testTag("progress_accuracy_metric"),
          )
        }
      }

      // Row 2: Word Cards Breakdown & Sentence Cards
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricStatCard(
            title = "واژه‌های یادگرفته‌شده / کل",
            value = "${analytics.learnedVocabularyCount} / ${analytics.totalWordCards}",
            icon = Icons.Rounded.Translate,
            modifier = Modifier.weight(1f).testTag("progress_words_breakdown_metric"),
          )
          MetricStatCard(
            title = "در حال یادگیری / سررسید",
            value = "${analytics.learningWordsCount} / ${analytics.dueWordsCount}",
            icon = Icons.Rounded.Style,
            modifier = Modifier.weight(1f).testTag("progress_learning_due_metric"),
          )
        }
      }

      // Row 3: Packages & All SRS Cards (preserving baseline tags)
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricStatCard(
            title = "بسته‌های تکمیل‌شده / کل",
            value = "${analytics.completedPackagesCount} / ${stats.totalPackages}",
            icon = Icons.Rounded.AutoStories,
            modifier = Modifier.weight(1f).testTag("progress_packages_metric"),
          )
          MetricStatCard(
            title = "کارت‌های SRS (واژه + ${analytics.totalSentenceCards} جمله)",
            value = stats.totalSrsCards.toString(),
            icon = Icons.Rounded.Style,
            modifier = Modifier.weight(1f).testTag("progress_srs_cards_metric"),
          )
        }
      }

      // 7-day Daily Activity Chart
      item {
        DailyActivityChartCard(
          points = analytics.dailyActivity,
          reviewedToday = analytics.reviewedTodayCount,
          totalReviewLogs = stats.totalReviewLogs,
        )
      }

      item {
        Text(
          text = "تاریخچه مطالعه و محتوای تکمیل‌شده",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      if (uiState.progressList.isEmpty()) {
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("progress_empty_reading_card"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            Text(
              text = "هنوز مطالعه‌ای ثبت نشده است. از تب یادگیری یک داستان یا گفت‌وگو را باز کنید تا پیشرفت شما ذخیره شود.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      } else {
        items(
          items = uiState.progressList,
          key = { it.progressId },
        ) { progress ->
          val totalSentences = progress.totalSentenceCount.coerceAtLeast(1)
          val ratio =
            (progress.completedSentenceCount.toFloat() / totalSentences.toFloat()).coerceIn(0f, 1f)
          val isCompleted = progress.completedSentenceCount >= totalSentences
          val isStillInLibrary = uiState.packagesById.containsKey(progress.packageId)

          Card(
            onClick = { if (isStillInLibrary) onOpenReader(progress.packageId) },
            modifier = Modifier.fillMaxWidth().testTag("progress_item_${progress.packageId}"),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                EnglishTextBlock(
                  text = progress.packageTitleSnapshot,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
                if (isCompleted) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                      Text(text = "تکمیل‌شده", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
              LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
              )
              Text(
                text =
                  "جملات مرور شده: ${progress.completedSentenceCount} از ${progress.totalSentenceCount}" +
                    if (progress.totalQuestionsCount > 0) {
                      " • درک مطلب: ${progress.correctQuestionsCount} از ${progress.totalQuestionsCount}"
                    } else {
                      ""
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (uiState.bookmarks.isNotEmpty()) {
        item {
          Text(
            text = "نشانک‌های اخیر (${uiState.bookmarks.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        items(
          items = uiState.bookmarks.take(10),
          key = { it.bookmarkId },
        ) { bookmark ->
          val canOpen = uiState.packagesById.containsKey(bookmark.packageId)
          Surface(
            onClick = { if (canOpen) onOpenReader(bookmark.packageId) },
            modifier = Modifier.fillMaxWidth().testTag("progress_bookmark_${bookmark.bookmarkId}"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EnglishTextBlock(
                  text = bookmark.packageTitleSnapshot,
                  style = MaterialTheme.typography.labelLarge,
                )
                Text(
                  text =
                    "جمله شماره ${bookmark.sentenceOrderIndex + 1}" +
                      (bookmark.noteFa?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                )
              }
              Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = "باز کردن در خواننده",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "تفکیک امن داده‌های محتوا و یادگیری",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text =
                "حذف یا به‌روزرسانی بسته‌های محتوا در جدول ContentPackages تأثیری بر کارت‌های SrsCards، سوابق SrsReviewLogs و واژگان ذخیره‌شده شما نمی‌گذارد.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DailyActivityChartCard(
  points: List<DailyActivityPoint>,
  reviewedToday: Int,
  totalReviewLogs: Int,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val total7Days = points.sumOf { it.reviewCount }
  val maxVal = (points.maxOfOrNull { it.reviewCount } ?: 0).coerceAtLeast(1)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("progress_7d_chart_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.progress_activity_7d_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "امروز: $reviewedToday • کل: $totalReviewLogs",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      if (total7Days == 0) {
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("progress_7d_chart_empty"),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
          Text(
            text = "در ۷ روز گذشته هنوز مروری ثبت نشده است. با انجام اولین جلسه مرور در بخش تمرین، نمودار فعالیت روزانه با داده‌های واقعی ترسیم می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
          )
        }
      } else {
        Canvas(
          modifier = Modifier.fillMaxWidth().height(110.dp),
        ) {
          val count = points.size.coerceAtLeast(1)
          val slotWidth = size.width / count
          val barWidth = (slotWidth * 0.45f).coerceAtMost(28.dp.toPx())
          val bottomPadding = 6.dp.toPx()
          val usableHeight = size.height - bottomPadding

          points.forEachIndexed { idx, point ->
            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
            // Track bar
            drawRoundRect(
              color = trackColor,
              topLeft = Offset(left, 0f),
              size = Size(barWidth, usableHeight),
              cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
            if (point.reviewCount > 0) {
              val ratio = point.reviewCount.toFloat() / maxVal.toFloat()
              val barHeight = (usableHeight * ratio).coerceAtLeast(8.dp.toPx())
              drawRoundRect(
                color = primaryColor,
                topLeft = Offset(left, usableHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          points.forEach { point ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${point.reviewCount}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = point.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
