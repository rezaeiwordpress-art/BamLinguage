package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.feature.components.EnglishTextBlock
import com.example.feature.components.MetricStatCard
import com.example.feature.components.OfflineStatusPill
import com.example.feature.components.UserVocabularyBottomSheet
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onNavigateToLearn: () -> Unit,
  onOpenReader: (String) -> Unit,
  onNavigateToPractice: () -> Unit,
  onNavigateToProgress: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val analytics = uiState.analytics
  var showVocabularySheet by remember { mutableStateOf(false) }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("home_dashboard_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        HeroBannerCard(
          onNavigateToLearn = onNavigateToLearn,
        )
      }

      uiState.feedbackMessage?.let { msg ->
        item {
          Surface(
            modifier = Modifier.fillMaxWidth().testTag("home_feedback_banner"),
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
              IconButton(onClick = viewModel::dismissFeedback, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن پیام")
              }
            }
          }
        }
      }

      // Core Room KPI Metrics (kept above the fold on handhelds)
      item {
        Text(
          text = stringResource(R.string.home_stats_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricStatCard(
            title = stringResource(R.string.home_packages_count),
            value = uiState.stats.totalPackages.toString(),
            icon = Icons.Rounded.AutoStories,
            modifier = Modifier.weight(1f).testTag("home_metric_packages"),
          )
          MetricStatCard(
            title = "واژه‌های یادگرفته‌شده",
            value = analytics.learnedVocabularyCount.toString(),
            icon = Icons.Rounded.Translate,
            modifier = Modifier.weight(1f).testTag("home_metric_glossary"),
          )
          MetricStatCard(
            title = stringResource(R.string.home_srs_due_count),
            value = "${analytics.dueTotalCount + analytics.newTotalCount}",
            icon = Icons.Rounded.Style,
            modifier = Modifier.weight(1f).testTag("home_metric_srs"),
          )
        }
      }

      // Daily Goal + Streak Card backed by real Room data
      item {
        DailyGoalAndStreakCard(
          reviewedToday = analytics.reviewedTodayCount,
          dailyGoal = uiState.dailyGoalCards,
          streakDays = analytics.streakDays,
          learnedWords = analytics.learnedVocabularyCount,
          newVocabularyCount = analytics.newWordsCount + uiState.userVocabulary.size,
          readingPercent = analytics.overallReadingPercent,
        )
      }

      if (uiState.isNewUserEmptyState) {
        item {
          OnboardingEmptyStateCard(
            onOpenFirstPackage = {
              uiState.featuredPackage?.let { onOpenReader(it.contentPackage.packageId) }
                ?: onNavigateToLearn()
            },
          )
        }
      }

      // Quick Access to Review, Vocabulary, Library, Progress
      item {
        Text(
          text = stringResource(R.string.home_quick_actions_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
          modifier = Modifier.fillMaxWidth().testTag("home_quick_actions_row"),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilledTonalButton(
            onClick = onNavigateToPractice,
            modifier = Modifier.testTag("home_quick_review_button"),
          ) {
            Icon(imageVector = Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("مرور (${analytics.dueTotalCount + analytics.newTotalCount})")
          }
          OutlinedButton(
            onClick = { showVocabularySheet = true },
            modifier = Modifier.testTag("home_user_vocab_button"),
          ) {
            Icon(imageVector = Icons.Rounded.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("واژه‌نامه شخصی (${uiState.userVocabulary.size})")
          }
          OutlinedButton(
            onClick = onNavigateToLearn,
            modifier = Modifier.testTag("home_quick_library_button"),
          ) {
            Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("کتابخانه (${uiState.stats.totalGlossaryEntries} واژه)")
          }
          OutlinedButton(
            onClick = onNavigateToProgress,
            modifier = Modifier.testTag("home_quick_progress_button"),
          ) {
            Icon(imageVector = Icons.Rounded.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("گزارش پیشرفت")
          }
        }
      }

      // Continue Reading Card
      item {
        val featured = uiState.featuredPackage
        Text(
          text =
            if ((uiState.continueReadingProgress?.completedSentenceCount ?: 0) > 0) "ادامه مطالعه"
            else "بسته پیشنهادی برای شروع",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (featured != null) {
          val completedSentences =
            (uiState.continueReadingProgress?.completedSentenceCount ?: 0).coerceAtMost(featured.sentenceCount)
          val totalSentences = featured.sentenceCount.coerceAtLeast(1)
          val fraction = (completedSentences.toFloat() / totalSentences.toFloat()).coerceIn(0f, 1f)

          Card(
            modifier = Modifier.fillMaxWidth().testTag("home_featured_package_card"),
            shape = MaterialTheme.shapes.large,
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
              ),
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
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                  Text(
                    text = "${featured.contentPackage.level.code} • ${featured.contentPackage.contentType.persianLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
                Text(
                  text = "نسخه ${featured.contentPackage.contentVersion} • ${(fraction * 100).toInt()}٪",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              EnglishTextBlock(
                text = featured.contentPackage.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
              )

              Text(
                text = featured.contentPackage.titleFa,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )

              Text(
                text = featured.contentPackage.descriptionFa,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
              )

              Text(
                text =
                  "$completedSentences از ${featured.sentenceCount} جمله خوانده شده • ${featured.glossaryCount} واژه • ${featured.questionCount} پرسش",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = { onOpenReader(featured.contentPackage.packageId) },
                  modifier = Modifier.weight(1f).testTag("home_open_reader_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(if (completedSentences > 0) "ادامه مطالعه" else "شروع مطالعه")
                }
                FilledTonalButton(
                  onClick = onNavigateToPractice,
                  modifier = Modifier.weight(1f).testTag("home_open_practice_button"),
                ) {
                  Text("جلسه مرور SRS")
                }
              }
            }
          }
        } else {
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
                text = "هنوز بسته محتوایی بارگذاری نشده است.",
                style = MaterialTheme.typography.bodyMedium,
              )
              FilledTonalButton(
                onClick = { viewModel.refreshBundledSample() },
                modifier = Modifier.testTag("home_seed_sample_button"),
              ) {
                Text(stringResource(R.string.learn_load_sample_button))
              }
            }
          }
        }
      }

      if (uiState.recentPackages.isNotEmpty()) {
        item {
          Text(
            text = stringResource(R.string.home_recent_content_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        items(
          items = uiState.recentPackages.take(4),
          key = { it.summary.contentPackage.packageId },
        ) { item ->
          val pkg = item.summary.contentPackage
          Surface(
            onClick = { onOpenReader(pkg.packageId) },
            modifier = Modifier.fillMaxWidth().testTag("home_recent_package_${pkg.packageId}"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                EnglishTextBlock(
                  text = pkg.title,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "${pkg.level.code} • ${pkg.contentType.persianLabel} • ${pkg.topic}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ) {
                Text(
                  text = "${item.completionPercent}٪",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showVocabularySheet) {
    UserVocabularyBottomSheet(
      items = uiState.userVocabulary,
      onSpeakWord = viewModel::speakWord,
      onPromoteToSrs = viewModel::promoteVocabularyToSrs,
      onDeleteItem = viewModel::deleteVocabularyItem,
      onDismiss = { showVocabularySheet = false },
    )
  }
}

@Composable
private fun DailyGoalAndStreakCard(
  reviewedToday: Int,
  dailyGoal: Int,
  streakDays: Int,
  learnedWords: Int,
  newVocabularyCount: Int,
  readingPercent: Int,
) {
  val safeGoal = dailyGoal.coerceAtLeast(1)
  val goalFraction = (reviewedToday.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1f)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("home_daily_goal_card"),
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
          text = stringResource(R.string.home_daily_goal_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          modifier = Modifier.testTag("home_streak_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.LocalFireDepartment,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "$streakDays روز پیوستگی",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      LinearProgressIndicator(
        progress = { goalFraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "مرور امروز: $reviewedToday از $safeGoal کارت",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "پیشرفت کل مطالعه: $readingPercent٪",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "واژگان جدید در صف/دفترچه: $newVocabularyCount",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "واژه‌های تثبیت‌شده: $learnedWords",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
}

@Composable
private fun OnboardingEmptyStateCard(
  onOpenFirstPackage: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("home_onboarding_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "راهنمای شروع سریع یادگیری آفلاین",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "۱. یک داستان یا گفت‌وگو را در خواننده باز کنید و به تلفظ جملات گوش دهید.\n۲. روی هر واژه ضربه بزنید تا معنی دقیق آن در جمله را ببینید و به کارت‌های مرور اضافه کنید.\n۳. هر روز از بخش تمرین، مرور هوشمند SM-2 را انجام دهید.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onOpenFirstPackage,
        modifier = Modifier.testTag("home_onboarding_start_button"),
      ) {
        Text("شروع اولین مطالعه")
      }
    }
  }
}

@Composable
private fun HeroBannerCard(
  onNavigateToLearn: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("home_hero_card"),
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column {
      Box(
        modifier = Modifier.fillMaxWidth().height(132.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_bamiyan_hero),
          contentDescription = "منظره بامیان بام‌لینگویج",
          modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraLarge),
          contentScale = ContentScale.Crop,
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                )
              ),
        )
        Column(
          modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OfflineStatusPill(label = stringResource(R.string.status_offline_ready))
          EnglishTextBlock(
            text = "BamLinguage",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
        }
      }

      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.home_welcome_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = stringResource(R.string.home_welcome_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
        )
        FilledTonalButton(
          onClick = onNavigateToLearn,
          modifier = Modifier.testTag("hero_explore_packages_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Layers,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("مرور کتابخانه داستان و گفت‌وگو")
        }
      }
    }
  }
}
