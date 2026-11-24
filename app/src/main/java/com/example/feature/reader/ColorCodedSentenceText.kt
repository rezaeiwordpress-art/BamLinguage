package com.example.feature.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.domain.model.ReaderWordStatus
import com.example.domain.usecase.ReaderTokenHighlight
import com.example.ui.theme.EnglishFontFamily

data class ReaderSrsPalette(
  val newBg: Color,
  val newText: Color,
  val learningBg: Color,
  val learningText: Color,
  val reviewBg: Color,
  val reviewText: Color,
  val dueBg: Color,
  val dueText: Color,
)

@Composable
fun rememberReaderSrsPalette(): ReaderSrsPalette {
  val isDark = isSystemInDarkTheme()
  val bg = MaterialTheme.colorScheme.background
  val darkMode = isDark || (bg.red + bg.green + bg.blue) < 1.2f
  return remember(darkMode) {
    if (darkMode) {
      ReaderSrsPalette(
        newBg = Color(0xFF2B3138),
        newText = Color(0xFFD6DBE0),
        learningBg = Color(0xFF4F3E0B),
        learningText = Color(0xFFFCD34D),
        reviewBg = Color(0xFF0E3F2E),
        reviewText = Color(0xFF6EE7B7),
        dueBg = Color(0xFF4C151B),
        dueText = Color(0xFFFCA5A5),
      )
    } else {
      ReaderSrsPalette(
        newBg = Color(0xFFEAE7E1),
        newText = Color(0xFF2F343A),
        learningBg = Color(0xFFFEF08A).copy(alpha = 0.72f),
        learningText = Color(0xFF92400E),
        reviewBg = Color(0xFFBBF7D0).copy(alpha = 0.72f),
        reviewText = Color(0xFF065F46),
        dueBg = Color(0xFFFECACA).copy(alpha = 0.78f),
        dueText = Color(0xFF991B1B),
      )
    }
  }
}

@Composable
fun ColorCodedSentenceText(
  sentenceText: String,
  highlights: List<ReaderTokenHighlight>,
  isCurrentlyPlaying: Boolean,
  onOffsetTapped: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = rememberReaderSrsPalette()
  val baseColor = MaterialTheme.colorScheme.onSurface
  var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

  val annotated: AnnotatedString =
    remember(sentenceText, highlights, palette, baseColor) {
      buildAnnotatedString {
        append(sentenceText)
        highlights.forEach { token ->
          val start = token.startOffset.coerceIn(0, sentenceText.length)
          val end = token.endOffset.coerceIn(start, sentenceText.length)
          if (start < end) {
            val (bg, fg, weight, decoration) =
              when (token.status) {
                ReaderWordStatus.NEW ->
                  QuadStyle(
                    palette.newBg.copy(alpha = 0.45f),
                    baseColor,
                    if (token.isPhrase) FontWeight.SemiBold else FontWeight.Normal,
                    if (token.isPhrase) TextDecoration.Underline else TextDecoration.None,
                  )
                ReaderWordStatus.LEARNING ->
                  QuadStyle(
                    palette.learningBg,
                    palette.learningText,
                    FontWeight.SemiBold,
                    TextDecoration.Underline,
                  )
                ReaderWordStatus.REVIEW ->
                  QuadStyle(
                    palette.reviewBg,
                    palette.reviewText,
                    FontWeight.SemiBold,
                    TextDecoration.Underline,
                  )
                ReaderWordStatus.DUE ->
                  QuadStyle(
                    palette.dueBg,
                    palette.dueText,
                    FontWeight.Bold,
                    TextDecoration.Underline,
                  )
              }
            addStyle(
              style =
                SpanStyle(
                  background = bg,
                  color = fg,
                  fontWeight = weight,
                  textDecoration = decoration,
                ),
              start = start,
              end = end,
            )
          }
        }
      }
    }

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Text(
      text = annotated,
      modifier =
        modifier.pointerInput(sentenceText) {
          detectTapGestures { offset ->
            layoutResult?.let { layout ->
              val charIndex = layout.getOffsetForPosition(offset)
              onOffsetTapped(charIndex)
            }
          }
        },
      style =
        MaterialTheme.typography.titleMedium.copy(
          fontFamily = EnglishFontFamily,
          fontSize = 17.sp,
          lineHeight = 28.sp,
          textDirection = TextDirection.Ltr,
          fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
        ),
      onTextLayout = { layoutResult = it },
    )
  }
}

private data class QuadStyle(
  val bg: Color,
  val fg: Color,
  val weight: FontWeight,
  val decoration: TextDecoration,
)
