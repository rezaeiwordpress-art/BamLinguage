package com.example.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.UserVocabularyItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserVocabularyBottomSheet(
  items: List<UserVocabularyItem>,
  onSpeakWord: (String) -> Unit,
  onPromoteToSrs: (UserVocabularyItem) -> Unit,
  onDeleteItem: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("user_vocabulary_bottom_sheet"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "واژه‌نامه شخصی (${items.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "واژه‌هایی که به صورت دستی از خواننده ذخیره کرده‌اید",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("user_vocab_close_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Close, contentDescription = "بستن")
        }
      }

      if (items.isEmpty()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
          Text(
            text = "هنوز واژه‌ای در واژه‌نامه شخصی ثبت نکرده‌اید. در صفحه خواننده، هنگام لمس واژه‌های ناشناس می‌توانید آن‌ها را ذخیره کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(320.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(items = items, key = { it.vocabId }) { item ->
            Surface(
              modifier = Modifier.fillMaxWidth().testTag("user_vocab_item_${item.vocabId}"),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    EnglishTextBlock(
                      text = item.displayTerm,
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                      text = item.persianMeaning,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                    item.notesFa?.takeIf { it.isNotBlank() }?.let { note ->
                      EnglishTextBlock(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSpeakWord(item.displayTerm) }) {
                      Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "پخش تلفظ",
                      )
                    }
                    IconButton(
                      onClick = { onDeleteItem(item.vocabId) },
                      modifier = Modifier.testTag("user_vocab_delete_${item.vocabId}"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.BookmarkRemove,
                        contentDescription = "حذف واژه",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
                OutlinedButton(
                  onClick = { onPromoteToSrs(item) },
                  modifier = Modifier.fillMaxWidth().testTag("user_vocab_add_srs_${item.vocabId}"),
                ) {
                  Icon(imageVector = Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.size(6.dp))
                  Text("افزودن به کارت‌های مرور (SRS)")
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
