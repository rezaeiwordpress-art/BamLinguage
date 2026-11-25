package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.app.AppContainer
import com.example.app.BamLinguageApplication
import com.example.core.datastore.UserPreferences
import com.example.feature.components.EnglishTextBlock
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.learn.LearnScreen
import com.example.feature.learn.LearnViewModel
import com.example.feature.navigation.BamLinguageRoute
import com.example.feature.navigation.HomeRoute
import com.example.feature.navigation.LearnRoute
import com.example.feature.navigation.PracticeRoute
import com.example.feature.navigation.ProfileRoute
import com.example.feature.navigation.ProgressRoute
import com.example.feature.navigation.ReaderRoute
import com.example.feature.navigation.bamLinguageDestinations
import com.example.feature.practice.PracticeScreen
import com.example.feature.practice.PracticeViewModel
import com.example.feature.profile.ProfileScreen
import com.example.feature.profile.ProfileViewModel
import com.example.feature.progress.ProgressScreen
import com.example.feature.progress.ProgressViewModel
import com.example.feature.reader.ReaderScreen
import com.example.feature.reader.ReaderViewModel
import com.example.ui.theme.BamLinguageTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val container = (application as BamLinguageApplication).container
    setContent {
      BamLinguageApp(container = container)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BamLinguageApp(container: AppContainer) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val appContext = LocalContext.current.applicationContext

  DisposableEffect(lifecycleOwner, container) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        container.timeRefreshSource.notifyForegrounded()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  DisposableEffect(appContext, container) {
    val timeReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          container.timeRefreshSource.notifyTimeChanged()
        }
      }
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
      }
    ContextCompat.registerReceiver(appContext, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    onDispose {
      runCatching { appContext.unregisterReceiver(timeReceiver) }
    }
  }

  val prefs by
    container.preferencesRepository.userPreferences.collectAsStateWithLifecycle(
      initialValue = UserPreferences(),
    )

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    BamLinguageTheme(themeMode = prefs.themeMode) {
      val navController = rememberNavController()
      val backStackEntry by navController.currentBackStackEntryAsState()
      val currentDestination = backStackEntry?.destination
      val isReaderRoute = currentDestination?.hasRoute(ReaderRoute::class) == true

      fun navigateToTab(route: BamLinguageRoute) {
        navController.navigate(route) {
          popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
          }
          launchSingleTop = true
          restoreState = true
        }
      }

      fun openReader(packageId: String) {
        navController.navigate(ReaderRoute(packageId = packageId))
      }

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          contentWindowInsets = WindowInsets.safeDrawing,
          topBar = {
            if (!isReaderRoute) {
              CenterAlignedTopAppBar(
                title = {
                  EnglishTextBlock(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                  )
                },
                colors =
                  TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                  ),
              )
            }
          },
          bottomBar = {
            if (!useNavigationRail && !isReaderRoute) {
              NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar"),
              ) {
                bamLinguageDestinations.forEach { dest ->
                  val selected = currentDestination?.hasRoute(dest.route::class) == true
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateToTab(dest.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = stringResource(dest.labelRes),
                      )
                    },
                    label = {
                      Text(
                        text = stringResource(dest.labelRes),
                        maxLines = 1,
                      )
                    },
                    modifier = Modifier.testTag(dest.testTag),
                  )
                }
              }
            }
          },
        ) { innerPadding ->
          Row(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
          ) {
            if (useNavigationRail && !isReaderRoute) {
              NavigationRail(
                modifier = Modifier.testTag("navigation_rail"),
              ) {
                bamLinguageDestinations.forEach { dest ->
                  val selected = currentDestination?.hasRoute(dest.route::class) == true
                  NavigationRailItem(
                    selected = selected,
                    onClick = { navigateToTab(dest.route) },
                    icon = {
                      Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = stringResource(dest.labelRes),
                      )
                    },
                    label = { Text(stringResource(dest.labelRes)) },
                    modifier = Modifier.testTag(dest.testTag),
                  )
                }
              }
            }

            NavHost(
              navController = navController,
              startDestination = HomeRoute,
              modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
              composable<HomeRoute> {
                val vm: HomeViewModel =
                  viewModel(
                    factory =
                      HomeViewModel.Factory(
                        contentRepository = container.contentRepository,
                        learningRepository = container.learningRepository,
                        preferencesRepository = container.preferencesRepository,
                        ensureBundledSamplePackageUseCase = container.ensureBundledSamplePackageUseCase,
                        createSrsCardUseCase = container.createSrsCardUseCase,
                        ttsEngine = container.ttsEngine,
                        timeRefreshSource = container.timeRefreshSource,
                      ),
                  )
                HomeScreen(
                  viewModel = vm,
                  onNavigateToLearn = { navigateToTab(LearnRoute) },
                  onOpenReader = ::openReader,
                  onNavigateToPractice = { navigateToTab(PracticeRoute) },
                  onNavigateToProgress = { navigateToTab(ProgressRoute) },
                )
              }

              composable<LearnRoute> {
                val vm: LearnViewModel =
                  viewModel(
                    factory =
                      LearnViewModel.Factory(
                        contentRepository = container.contentRepository,
                        learningRepository = container.learningRepository,
                        preferencesRepository = container.preferencesRepository,
                        importContentPackageUseCase = container.importContentPackageUseCase,
                        ensureBundledSamplePackageUseCase = container.ensureBundledSamplePackageUseCase,
                        dictionaryLookupUseCase = container.dictionaryLookupUseCase,
                        createSrsCardUseCase = container.createSrsCardUseCase,
                        contentIntegrityAnalyzer = container.contentIntegrityAnalyzer,
                        ttsEngine = container.ttsEngine,
                      ),
                  )
                LearnScreen(
                  viewModel = vm,
                  onOpenReader = ::openReader,
                )
              }

              composable<ReaderRoute> { entry ->
                val route = entry.toRoute<ReaderRoute>()
                val vm: ReaderViewModel =
                  viewModel(
                    key = "reader_${route.packageId}",
                    factory =
                      ReaderViewModel.Factory(
                        packageId = route.packageId,
                        observeReaderPackageUseCase = container.observeReaderPackageUseCase,
                        resolveWordInSentenceUseCase = container.resolveWordInSentenceUseCase,
                        createSrsCardUseCase = container.createSrsCardUseCase,
                        learningRepository = container.learningRepository,
                        preferencesRepository = container.preferencesRepository,
                        ttsEngine = container.ttsEngine,
                        aiService = container.aiService,
                        timeRefreshSource = container.timeRefreshSource,
                      ),
                  )
                ReaderScreen(
                  viewModel = vm,
                  onNavigateBack = { navController.popBackStack() },
                )
              }

              composable<PracticeRoute> {
                val vm: PracticeViewModel =
                  viewModel(
                    factory =
                      PracticeViewModel.Factory(
                        ttsEngine = container.ttsEngine,
                        learningRepository = container.learningRepository,
                        preferencesRepository = container.preferencesRepository,
                        srsScheduler = container.srsScheduler,
                        reviewSrsCardUseCase = container.reviewSrsCardUseCase,
                        deleteSrsCardUseCase = container.deleteSrsCardUseCase,
                        timeRefreshSource = container.timeRefreshSource,
                      ),
                  )
                PracticeScreen(viewModel = vm)
              }

              composable<ProgressRoute> {
                val vm: ProgressViewModel =
                  viewModel(
                    factory =
                      ProgressViewModel.Factory(
                        contentRepository = container.contentRepository,
                        learningRepository = container.learningRepository,
                        timeRefreshSource = container.timeRefreshSource,
                      ),
                  )
                ProgressScreen(
                  viewModel = vm,
                  onOpenReader = ::openReader,
                )
              }

              composable<ProfileRoute> {
                val vm: ProfileViewModel =
                  viewModel(
                    factory =
                      ProfileViewModel.Factory(
                        preferencesRepository = container.preferencesRepository,
                        learningRepository = container.learningRepository,
                        ttsEngine = container.ttsEngine,
                        ensureBundledSamplePackageUseCase = container.ensureBundledSamplePackageUseCase,
                        aiService = container.aiService,
                      ),
                  )
                ProfileScreen(viewModel = vm)
              }
            }
          }
        }
      }
    }
  }
}
