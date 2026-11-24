package com.example.feature.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

@Serializable
sealed interface BamLinguageRoute

@Serializable
data object HomeRoute : BamLinguageRoute

@Serializable
data object LearnRoute : BamLinguageRoute

@Serializable
data class ReaderRoute(val packageId: String) : BamLinguageRoute

@Serializable
data object PracticeRoute : BamLinguageRoute

@Serializable
data object ProgressRoute : BamLinguageRoute

@Serializable
data object ProfileRoute : BamLinguageRoute

data class BottomNavDestination(
  val route: BamLinguageRoute,
  val testTag: String,
  @param:StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

val bamLinguageDestinations =
  listOf(
    BottomNavDestination(
      route = HomeRoute,
      testTag = "nav_tab_home",
      labelRes = R.string.nav_home,
      selectedIcon = Icons.Rounded.Home,
      unselectedIcon = Icons.Outlined.Home,
    ),
    BottomNavDestination(
      route = LearnRoute,
      testTag = "nav_tab_learn",
      labelRes = R.string.nav_learn,
      selectedIcon = Icons.Rounded.MenuBook,
      unselectedIcon = Icons.Outlined.MenuBook,
    ),
    BottomNavDestination(
      route = PracticeRoute,
      testTag = "nav_tab_practice",
      labelRes = R.string.nav_practice,
      selectedIcon = Icons.Rounded.RecordVoiceOver,
      unselectedIcon = Icons.Outlined.RecordVoiceOver,
    ),
    BottomNavDestination(
      route = ProgressRoute,
      testTag = "nav_tab_progress",
      labelRes = R.string.nav_progress,
      selectedIcon = Icons.Rounded.Insights,
      unselectedIcon = Icons.Outlined.Insights,
    ),
    BottomNavDestination(
      route = ProfileRoute,
      testTag = "nav_tab_profile",
      labelRes = R.string.nav_profile,
      selectedIcon = Icons.Rounded.Person,
      unselectedIcon = Icons.Outlined.Person,
    ),
  )
