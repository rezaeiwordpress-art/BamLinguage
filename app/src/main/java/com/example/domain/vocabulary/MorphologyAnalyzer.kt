package com.example.domain.vocabulary

/**
 * Morphology and irregular-inflection analyzer that avoids naive suffix chopping.
 * Supports irregular verbs, irregular nouns, comparative/superlative forms (including
 * multi-lemma candidates like better -> [good, well]), and orthographic inflection rules.
 */
object MorphologyAnalyzer {

  data class LemmaCandidate(
    val lemma: String,
    val hintedPos: String? = null,
    val confidence: Float = 1.0f,
  )

  private val irregularMap: Map<String, List<LemmaCandidate>> =
    mapOf(
      "am" to listOf(LemmaCandidate("be", "verb")),
      "is" to listOf(LemmaCandidate("be", "verb")),
      "are" to listOf(LemmaCandidate("be", "verb")),
      "was" to listOf(LemmaCandidate("be", "verb")),
      "were" to listOf(LemmaCandidate("be", "verb")),
      "been" to listOf(LemmaCandidate("be", "verb")),
      "being" to listOf(LemmaCandidate("be", "verb")),
      "has" to listOf(LemmaCandidate("have", "verb")),
      "had" to listOf(LemmaCandidate("have", "verb")),
      "having" to listOf(LemmaCandidate("have", "verb")),
      "does" to listOf(LemmaCandidate("do", "verb")),
      "did" to listOf(LemmaCandidate("do", "verb")),
      "done" to listOf(LemmaCandidate("do", "verb")),
      "doing" to listOf(LemmaCandidate("do", "verb")),
      "went" to listOf(LemmaCandidate("go", "verb")),
      "gone" to listOf(LemmaCandidate("go", "verb")),
      "goes" to listOf(LemmaCandidate("go", "verb")),
      "going" to listOf(LemmaCandidate("go", "verb")),
      "better" to listOf(LemmaCandidate("good", "adjective", 0.9f), LemmaCandidate("well", "adverb", 0.9f)),
      "best" to listOf(LemmaCandidate("good", "adjective", 0.9f), LemmaCandidate("well", "adverb", 0.9f)),
      "worse" to listOf(LemmaCandidate("bad", "adjective", 0.9f), LemmaCandidate("badly", "adverb", 0.85f)),
      "worst" to listOf(LemmaCandidate("bad", "adjective", 0.9f), LemmaCandidate("badly", "adverb", 0.85f)),
      "more" to listOf(LemmaCandidate("much", "determiner", 0.85f), LemmaCandidate("many", "determiner", 0.85f)),
      "most" to listOf(LemmaCandidate("much", "determiner", 0.85f), LemmaCandidate("many", "determiner", 0.85f)),
      "less" to listOf(LemmaCandidate("little", "adjective", 0.9f)),
      "least" to listOf(LemmaCandidate("little", "adjective", 0.9f)),
      "ran" to listOf(LemmaCandidate("run", "verb")),
      "running" to listOf(LemmaCandidate("run", "verb")),
      "runs" to listOf(LemmaCandidate("run", "verb")),
      "came" to listOf(LemmaCandidate("come", "verb")),
      "coming" to listOf(LemmaCandidate("come", "verb")),
      "took" to listOf(LemmaCandidate("take", "verb")),
      "taken" to listOf(LemmaCandidate("take", "verb")),
      "taking" to listOf(LemmaCandidate("take", "verb")),
      "gave" to listOf(LemmaCandidate("give", "verb")),
      "given" to listOf(LemmaCandidate("give", "verb")),
      "giving" to listOf(LemmaCandidate("give", "verb")),
      "saw" to listOf(LemmaCandidate("see", "verb")),
      "seen" to listOf(LemmaCandidate("see", "verb")),
      "made" to listOf(LemmaCandidate("make", "verb")),
      "making" to listOf(LemmaCandidate("make", "verb")),
      "said" to listOf(LemmaCandidate("say", "verb")),
      "knew" to listOf(LemmaCandidate("know", "verb")),
      "known" to listOf(LemmaCandidate("know", "verb")),
      "thought" to listOf(LemmaCandidate("think", "verb")),
      "caught" to listOf(LemmaCandidate("catch", "verb")),
      "taught" to listOf(LemmaCandidate("teach", "verb")),
      "bought" to listOf(LemmaCandidate("buy", "verb")),
      "brought" to listOf(LemmaCandidate("bring", "verb")),
      "felt" to listOf(LemmaCandidate("feel", "verb")),
      "kept" to listOf(LemmaCandidate("keep", "verb")),
      "left" to listOf(LemmaCandidate("leave", "verb"), LemmaCandidate("left", "adjective", 0.8f)),
      "lost" to listOf(LemmaCandidate("lose", "verb")),
      "met" to listOf(LemmaCandidate("meet", "verb")),
      "sat" to listOf(LemmaCandidate("sit", "verb")),
      "sitting" to listOf(LemmaCandidate("sit", "verb")),
      "stood" to listOf(LemmaCandidate("stand", "verb")),
      "wrote" to listOf(LemmaCandidate("write", "verb")),
      "written" to listOf(LemmaCandidate("write", "verb")),
      "writing" to listOf(LemmaCandidate("write", "verb")),
      "spoke" to listOf(LemmaCandidate("speak", "verb")),
      "spoken" to listOf(LemmaCandidate("speak", "verb")),
      "speaking" to listOf(LemmaCandidate("speak", "verb")),
      "broke" to listOf(LemmaCandidate("break", "verb")),
      "broken" to listOf(LemmaCandidate("break", "verb")),
      "chose" to listOf(LemmaCandidate("choose", "verb")),
      "chosen" to listOf(LemmaCandidate("choose", "verb")),
      "drove" to listOf(LemmaCandidate("drive", "verb")),
      "driven" to listOf(LemmaCandidate("drive", "verb")),
      "ate" to listOf(LemmaCandidate("eat", "verb")),
      "eaten" to listOf(LemmaCandidate("eat", "verb")),
      "fell" to listOf(LemmaCandidate("fall", "verb")),
      "fallen" to listOf(LemmaCandidate("fall", "verb")),
      "flew" to listOf(LemmaCandidate("fly", "verb")),
      "flown" to listOf(LemmaCandidate("fly", "verb")),
      "forgot" to listOf(LemmaCandidate("forget", "verb")),
      "forgotten" to listOf(LemmaCandidate("forget", "verb")),
      "got" to listOf(LemmaCandidate("get", "verb")),
      "gotten" to listOf(LemmaCandidate("get", "verb")),
      "getting" to listOf(LemmaCandidate("get", "verb")),
      "heard" to listOf(LemmaCandidate("hear", "verb")),
      "held" to listOf(LemmaCandidate("hold", "verb")),
      "paid" to listOf(LemmaCandidate("pay", "verb")),
      "rode" to listOf(LemmaCandidate("ride", "verb")),
      "ridden" to listOf(LemmaCandidate("ride", "verb")),
      "slept" to listOf(LemmaCandidate("sleep", "verb")),
      "spent" to listOf(LemmaCandidate("spend", "verb")),
      "told" to listOf(LemmaCandidate("tell", "verb")),
      "woke" to listOf(LemmaCandidate("wake", "verb")),
      "woken" to listOf(LemmaCandidate("wake", "verb")),
      "wore" to listOf(LemmaCandidate("wear", "verb")),
      "worn" to listOf(LemmaCandidate("wear", "verb")),
      "won" to listOf(LemmaCandidate("win", "verb")),
      "found" to listOf(LemmaCandidate("find", "verb")),
      "men" to listOf(LemmaCandidate("man", "noun")),
      "women" to listOf(LemmaCandidate("woman", "noun")),
      "children" to listOf(LemmaCandidate("child", "noun")),
      "feet" to listOf(LemmaCandidate("foot", "noun")),
      "teeth" to listOf(LemmaCandidate("tooth", "noun")),
      "mice" to listOf(LemmaCandidate("mouse", "noun")),
      "people" to listOf(LemmaCandidate("person", "noun")),
      "leaves" to listOf(LemmaCandidate("leaf", "noun", 0.85f), LemmaCandidate("leave", "verb", 0.85f)),
    )

  /**
   * Words ending in -s / -ed / -ing / -er that MUST NOT be stripped naively.
   */
  private val invariantWords =
    setOf(
      "his", "her", "this", "yes", "is", "was", "has", "does", "us", "thus", "plus",
      "bus", "gas", "glass", "class", "grass", "pass", "cross", "miss", "kiss", "less",
      "news", "lens", "series", "species", "morning", "evening", "spring", "bring",
      "sing", "ring", "king", "wing", "thing", "string", "bed", "red", "fed", "led",
      "shed", "sled", "speed", "need", "seed", "feed", "indeed", "water", "better",
      "never", "ever", "after", "under", "over", "other", "another", "together",
      "her", "their", "our", "your", "sister", "brother", "mother", "father", "winter",
      "summer", "number", "paper", "letter", "matter", "later", "earlier", "train",
    )

  fun normalizeToken(raw: String): String =
    raw.trim().trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }.lowercase()

  /**
   * Returns ordered lemma candidates for [surfaceToken].
   * Always checks irregular table and orthographic rules guarded against invariant words.
   */
  fun resolveLemmas(surfaceToken: String): List<LemmaCandidate> {
    val token = normalizeToken(surfaceToken)
    if (token.isEmpty()) return emptyList()

    val candidates = linkedMapOf<String, LemmaCandidate>()

    irregularMap[token]?.forEach { candidate ->
      candidates.putIfAbsent(candidate.lemma, candidate)
    }

    if (token in invariantWords) {
      candidates.putIfAbsent(token, LemmaCandidate(token, null, 1.0f))
      return candidates.values.toList()
    }

    // Orthographic rules with strict minimum-length & consonant guards (no blind suffix chopping)
    if (token.endsWith("ies") && token.length > 4) {
      val base = token.dropLast(3) + "y"
      candidates.putIfAbsent(base, LemmaCandidate(base, null, 0.85f))
    }
    if (token.endsWith("ied") && token.length > 4) {
      val base = token.dropLast(3) + "y"
      candidates.putIfAbsent(base, LemmaCandidate(base, "verb", 0.85f))
    }
    if (token.endsWith("ves") && token.length > 4) {
      val baseF = token.dropLast(3) + "f"
      val baseFe = token.dropLast(3) + "fe"
      candidates.putIfAbsent(baseF, LemmaCandidate(baseF, "noun", 0.8f))
      candidates.putIfAbsent(baseFe, LemmaCandidate(baseFe, "noun", 0.8f))
    }
    if (token.endsWith("ing") && token.length > 5) {
      val stem = token.dropLast(3)
      if (stem.length >= 3 && stem.last() == stem[stem.length - 2] && isCvcDoubledConsonant(stem.last())) {
        val undoubled = stem.dropLast(1)
        candidates.putIfAbsent(undoubled, LemmaCandidate(undoubled, "verb", 0.88f))
        candidates.putIfAbsent(stem, LemmaCandidate(stem, "verb", 0.80f))
      } else {
        candidates.putIfAbsent(stem, LemmaCandidate(stem, "verb", 0.85f))
        if (stem.lastOrNull()?.let { isConsonant(it) } == true) {
          candidates.putIfAbsent(stem + "e", LemmaCandidate(stem + "e", "verb", 0.8f))
        }
      }
    }
    if (token.endsWith("ed") && token.length > 4) {
      val stem = token.dropLast(2)
      if (stem.length >= 3 && stem.last() == stem[stem.length - 2] && isCvcDoubledConsonant(stem.last())) {
        val undoubled = stem.dropLast(1)
        candidates.putIfAbsent(undoubled, LemmaCandidate(undoubled, "verb", 0.88f))
        candidates.putIfAbsent(stem, LemmaCandidate(stem, "verb", 0.80f))
      } else {
        candidates.putIfAbsent(stem, LemmaCandidate(stem, "verb", 0.86f))
        candidates.putIfAbsent(token.dropLast(1), LemmaCandidate(token.dropLast(1), "verb", 0.8f))
      }
    }
    if (token.endsWith("es") && token.length > 4) {
      val base2 = token.dropLast(2)
      if (base2.endsWith("sh") || base2.endsWith("ch") || base2.endsWith("x") || base2.endsWith("s") || base2.endsWith("z") || base2.endsWith("o")) {
        candidates.putIfAbsent(base2, LemmaCandidate(base2, null, 0.84f))
      } else {
        candidates.putIfAbsent(token.dropLast(1), LemmaCandidate(token.dropLast(1), null, 0.8f))
      }
    } else if (token.endsWith("s") && !token.endsWith("ss") && token.length > 3) {
      candidates.putIfAbsent(token.dropLast(1), LemmaCandidate(token.dropLast(1), null, 0.82f))
    }

    candidates.putIfAbsent(token, LemmaCandidate(token, null, 1.0f))
    return candidates.values.toList()
  }

  private fun isConsonant(c: Char): Boolean =
    c in 'a'..'z' && c !in setOf('a', 'e', 'i', 'o', 'u')

  private fun isCvcDoubledConsonant(c: Char): Boolean =
    isConsonant(c) && c !in setOf('s', 'l', 'f', 'z')
}
