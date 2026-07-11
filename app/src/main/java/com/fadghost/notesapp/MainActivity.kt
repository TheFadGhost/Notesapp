package com.fadghost.notesapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.fadghost.notesapp.data.prefs.ThemeMode
import com.fadghost.notesapp.data.work.DiaryNudgeWorker
import com.fadghost.notesapp.ui.MainViewModel
import com.fadghost.notesapp.notify.ReminderNotifier
import com.fadghost.notesapp.ui.calendar.CalendarDeepLink
import com.fadghost.notesapp.ui.diary.DiaryLaunch
import com.fadghost.notesapp.ui.diary.DiaryLockManager
import com.fadghost.notesapp.ui.shell.AppShell
import com.fadghost.notesapp.ui.theme.AuraTheme
import com.fadghost.notesapp.ui.theme.DarkTokens
import com.fadghost.notesapp.ui.theme.LightTokens
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity. Extends [FragmentActivity] because the diary biometric gate uses
 * androidx.biometric's [androidx.biometric.BiometricPrompt], which attaches to a
 * FragmentActivity (PLAN.md §7). Also drives the diary lock lifecycle (re-lock after
 * >30s in background) and routes the journaling-nudge deep link to the Diary tab.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var diaryLockManager: DiaryLockManager
    @Inject lateinit var diaryLaunch: DiaryLaunch

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent { NotesRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        diaryLockManager.onEnterForeground()
    }

    override fun onStop() {
        super.onStop()
        diaryLockManager.onEnterBackground()
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(DiaryNudgeWorker.EXTRA_OPEN_DIARY, false) == true) {
            diaryLaunch.requestOpenDiary()
        }
        // Reminder-notification tap → open the item on the Calendar tab (PLAN.md §8).
        if (intent?.getBooleanExtra(ReminderNotifier.EXTRA_OPEN_CALENDAR, false) == true) {
            CalendarDeepLink.request(intent.getLongExtra(ReminderNotifier.EXTRA_REMINDER_ID, -1L))
        }
    }
}

@Composable
private fun NotesRoot(viewModel: MainViewModel = hiltViewModel()) {
    val mode by viewModel.themeMode.collectAsState()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    AuraTheme(tokens = if (dark) DarkTokens else LightTokens) {
        AppShell(
            themeMode = mode,
            onSelectThemeMode = viewModel::setThemeMode
        )
    }
}
