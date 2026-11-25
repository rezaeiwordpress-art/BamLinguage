package com.example.core.common

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Deterministic, collision-resistant identifier generator backed by SHA-256.
 * Replaces unstable 32-bit [String.hashCode] keys so identifiers survive across
 * JVM/Android runs, process restarts, and repeated package imports.
 */
object StableIdGenerator {

  fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
      val v = b.toInt() and 0xFF
      sb.append(HEX_CHARS[v ushr 4])
      sb.append(HEX_CHARS[v and 0x0F])
    }
    return sb.toString()
  }

  fun normalizeKeyPart(raw: String, fallback: String = ""): String =
    raw.trim().lowercase().replace(WHITESPACE_REGEX, " ").ifBlank { fallback }

  fun buildUserVocabId(normalizedTerm: String): String {
    val clean = normalizeKeyPart(normalizedTerm, fallback = "unknown")
    return "vocab_${sha256Hex(clean).take(24)}"
  }

  fun buildWordContentId(
    normalizedLemma: String,
    pos: String,
    senseId: String,
  ): String {
    val cleanLemma = escapeSegment(normalizeKeyPart(normalizedLemma, fallback = "unknown"))
    val cleanPos = escapeSegment(normalizeKeyPart(pos, fallback = "word"))
    val cleanSense = escapeSegment(normalizeKeyPart(senseId, fallback = "sense-1"))
    return "word:$cleanLemma:$cleanPos:$cleanSense"
  }

  fun buildSentenceContentId(
    packageId: String?,
    sourceSentenceId: String?,
    englishText: String,
  ): String {
    val cleanPkg = packageId?.trim().orEmpty()
    val cleanSentenceId = sourceSentenceId?.trim().orEmpty()
    return if (cleanPkg.isNotEmpty() && cleanSentenceId.isNotEmpty()) {
      "sentence:$cleanPkg:$cleanSentenceId"
    } else {
      val cleanText = normalizeKeyPart(englishText, fallback = "empty")
      "sentence:text:${sha256Hex(cleanText).take(32)}"
    }
  }

  fun buildQuizAttemptId(packageId: String, questionId: String): String {
    val composite = "${packageId.trim()}\u0000${questionId.trim()}"
    return "qa_${sha256Hex(composite).take(24)}"
  }

  private fun escapeSegment(value: String): String =
    value.replace("%", "%25").replace(":", "%3A")

  private val WHITESPACE_REGEX = Regex("\\s+")
  private val HEX_CHARS = "0123456789abcdef".toCharArray()
}
