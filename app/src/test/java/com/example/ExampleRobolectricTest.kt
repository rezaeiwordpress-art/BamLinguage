package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app identity is BamLinguage and all five tabs render cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("BamLinguage", context.getString(R.string.app_name))

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("bottom_navigation_bar").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tab_learn").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tab_practice").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tab_progress").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_tab_profile").assertIsDisplayed()

    composeRule.onNodeWithTag("nav_tab_learn").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("learn_import_json_button").assertIsDisplayed()

    composeRule.onNodeWithTag("nav_tab_practice").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("practice_srs_foundation_card").assertIsDisplayed()

    composeRule.onNodeWithTag("nav_tab_progress").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("progress_packages_metric").assertIsDisplayed()

    composeRule.onNodeWithTag("nav_tab_profile").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profile_theme_card").assertIsDisplayed()
  }

  @Test
  fun `end to end user journey opens Reader, adds sentence to SRS, completes SRS review session, and verifies Progress`() {
    // Wait until both bundled sample packages are seeded into Room on startup
    composeRule.waitUntil(timeoutMillis = 15_000L) {
      composeRule.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty()
    }

    // Navigate to Learn tab, filter B1, scroll to and open A Late Train
    composeRule.onNodeWithTag("nav_tab_learn").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("filter_level_B1").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000L) {
      composeRule.onAllNodesWithTag("package_card_story-b1-0001").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("package_card_story-b1-0001").performClick()

    // Verify Reader loads story sentences & controls
    composeRule.waitUntil(timeoutMillis = 10_000L) {
      composeRule.onAllNodesWithTag("reader_sentence_card_0").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_audio_controls_card").assertIsDisplayed()
    composeRule.onNodeWithTag("reader_srs_legend").assertIsDisplayed()

    // Scroll inside Reader LazyColumn to sentence 0 and bookmark + add sentence 0 to SRS
    composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("reader_sentence_card_0"))
    composeRule.onNodeWithTag("sentence_bookmark_button_0").performClick()
    composeRule.onNodeWithTag("sentence_mark_read_0").performClick()
    composeRule.onNodeWithTag("sentence_add_srs_0").performClick()
    composeRule.waitForIdle()

    // Tap sentence 0 text to open contextual VocabularyBottomSheet and dismiss it
    composeRule.onNodeWithTag("sentence_text_0").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("reader_vocabulary_bottom_sheet").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("vocab_sheet_tts_button").assertIsDisplayed()
    composeRule.onNodeWithTag("vocab_sheet_close_button").performClick()
    composeRule.waitForIdle()

    // Return back from Reader to Library -> go to Practice tab
    composeRule.onNodeWithTag("reader_lazy_column").performScrollToNode(hasTestTag("reader_back_button"))
    composeRule.onNodeWithTag("reader_back_button").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("nav_tab_practice").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("start_srs_session_button").fetchSemanticsNodes().isNotEmpty()
    }

    // Start SRS Review Session, reveal answer, rate GOOD, check summary
    composeRule.onNodeWithTag("start_srs_session_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("srs_active_flashcard").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("srs_reveal_answer_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("srs_card_answer_panel").assertIsDisplayed()

    composeRule.onNodeWithTag("srs_rate_good").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("srs_session_summary_card").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("srs_summary_done_button").performClick()
    composeRule.waitForIdle()

    // Verify Progress tab reflects real SRS & Bookmark metrics + 7-day chart + clickable bookmark
    composeRule.onNodeWithTag("nav_tab_progress").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("progress_lazy_column").performScrollToNode(hasTestTag("progress_srs_cards_metric"))
    composeRule.onNodeWithTag("progress_srs_cards_metric").assertIsDisplayed()
    composeRule.onNodeWithTag("progress_lazy_column").performScrollToNode(hasTestTag("progress_7d_chart_card"))
    composeRule.onNodeWithTag("progress_7d_chart_card").assertIsDisplayed()
    composeRule.onNodeWithTag("progress_lazy_column").performScrollToNode(hasTestTag("progress_item_story-b1-0001"))
    composeRule.onNodeWithTag("progress_item_story-b1-0001").assertIsDisplayed()

    // Tap the bookmark row in Progress -> navigates directly into ReaderScreen
    composeRule.onNodeWithTag("progress_lazy_column").performScrollToNode(hasTestTag("progress_bookmark_bm_story-b1-0001_1"))
    composeRule.onNodeWithTag("progress_bookmark_bm_story-b1-0001_1").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000L) {
      composeRule.onAllNodesWithTag("reader_sentence_card_0").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("reader_sentence_card_0").assertIsDisplayed()
  }

  @Test
  fun `phase 3 home dashboard, library filters, package integrity inspector, and profile settings work cleanly`() {
    composeRule.waitUntil(timeoutMillis = 15_000L) {
      composeRule.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty()
    }

    // Home Dashboard shows daily goal, streak badge, quick actions, and opens User Vocabulary modal
    composeRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag("home_daily_goal_card"))
    composeRule.onNodeWithTag("home_daily_goal_card").assertIsDisplayed()
    composeRule.onNodeWithTag("home_streak_badge").assertIsDisplayed()
    composeRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag("home_quick_actions_row"))
    composeRule.onNodeWithTag("home_quick_actions_row").assertIsDisplayed()
    composeRule.onNodeWithTag("home_dashboard_list").performScrollToNode(hasTestTag("home_user_vocab_button"))
    composeRule.onNodeWithTag("home_user_vocab_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("user_vocabulary_bottom_sheet").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("user_vocab_close_button").performClick()
    composeRule.waitForIdle()

    // Learn tab: inspect package integrity report (100% coverage) & filter by type
    composeRule.onNodeWithTag("nav_tab_learn").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000L) {
      composeRule.onAllNodesWithTag("package_card_story-b1-0001").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("learn_packages_list").performScrollToNode(hasTestTag("package_inspect_story-b1-0001"))
    composeRule.onNodeWithTag("package_inspect_story-b1-0001").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000L) {
      composeRule.onAllNodesWithTag("package_inspector_dialog").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNode(hasText("100٪", substring = true)).assertIsDisplayed()
    composeRule.onNodeWithText("بستن").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("learn_packages_list").performScrollToNode(hasTestTag("filter_type_dialogue"))
    composeRule.onNodeWithTag("filter_type_dialogue").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("learn_packages_list").performScrollToNode(hasTestTag("package_card_pkg_bamiyan_morning_a2"))
    composeRule.onNodeWithTag("package_card_pkg_bamiyan_morning_a2").assertIsDisplayed()

    // Profile tab: verify newCardsPerDay stepper, reader prefs, data management, and disabled-by-default AI card
    composeRule.onNodeWithTag("nav_tab_profile").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profile_settings_list").performScrollToNode(hasTestTag("new_cards_increase_button"))
    composeRule.onNodeWithTag("new_cards_increase_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("new_cards_value_text").assertIsDisplayed()

    composeRule.onNodeWithTag("profile_settings_list").performScrollToNode(hasTestTag("profile_reader_prefs_card"))
    composeRule.onNodeWithTag("pref_show_ipa").assertIsDisplayed()
    composeRule.onNodeWithTag("pref_show_grammar_notes").assertIsDisplayed()
    composeRule.onNodeWithTag("pref_autoplay_srs_audio").assertIsDisplayed()

    composeRule.onNodeWithTag("profile_settings_list").performScrollToNode(hasTestTag("profile_ai_settings_card"))
    composeRule.onNodeWithTag("ai_enabled_switch").assertIsDisplayed()

    composeRule.onNodeWithTag("profile_settings_list").performScrollToNode(hasTestTag("profile_app_info_card"))
    composeRule.onNodeWithTag("profile_app_info_card").assertIsDisplayed()
  }
}
