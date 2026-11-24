package com.example.domain.usecase

import com.example.domain.model.GlossaryEntry
import com.example.domain.model.UserVocabularyItem
import com.example.domain.repository.ContentRepository
import com.example.domain.repository.LearningRepository
import kotlinx.coroutines.flow.first

data class DictionaryLookupResult(
  val normalizedQuery: String,
  val glossaryMatches: List<GlossaryEntry>,
  val savedVocabularyMatches: List<UserVocabularyItem>,
)

class DictionaryLookupUseCase(
  private val contentRepository: ContentRepository,
  private val learningRepository: LearningRepository,
) {
  suspend operator fun invoke(rawQuery: String): DictionaryLookupResult {
    val normalized = rawQuery.trim().lowercase()
    if (normalized.isEmpty()) {
      return DictionaryLookupResult(
        normalizedQuery = "",
        glossaryMatches = emptyList(),
        savedVocabularyMatches = emptyList(),
      )
    }

    val glossaryMatches = contentRepository.searchGlossary(normalized)
    val savedMatches =
      learningRepository.observeUserVocabulary().first().filter {
        it.normalizedTerm.contains(normalized, ignoreCase = true) ||
          it.displayTerm.contains(normalized, ignoreCase = true)
      }

    return DictionaryLookupResult(
      normalizedQuery = normalized,
      glossaryMatches = glossaryMatches,
      savedVocabularyMatches = savedMatches,
    )
  }
}
