package com.emotionalleaves.ballot.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.emotionalleaves.ballot.agent.Card
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Gap
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.ThickRule
import com.emotionalleaves.ballot.ui.screens.*
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors

object Routes {
    const val HOME = "home"
    const val CHIEF = "chief"
    const val PATH = "path"
    const val PEOPLE = "people"
    const val MORE = "more"
    const val APPROVALS = "approvals"
    const val GROUND = "ground"
    const val BRIEFING = "briefing"
    const val FIELD = "field"
    const val COMPASS = "compass"
    const val GYM = "gym"
    const val STUDIO = "studio"
    const val VAULT = "vault"
    const val HQ = "hq"
    const val SETTINGS = "settings"
    const val CASEFILE = "casefile"
    const val QUIZ = "quiz"
    const val GONOGO = "gonogo"
    const val REVIEW = "review"
    const val CONTACT = "contact/{id}"
    const val DOCUMENT = "document/{id}"
    const val ROOM = "room/{id}"
    fun contact(id: Long) = "contact/$id"
    fun document(id: Long) = "document/$id"
    fun room(id: String) = "room/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.CHIEF, "Chief", Icons.Filled.Forum),
    Tab(Routes.PATH, "Path", Icons.Filled.Timeline),
    Tab(Routes.PEOPLE, "People", Icons.Filled.Groups),
    Tab(Routes.MORE, "More", Icons.Filled.Apps),
)

/** Opens whatever a Chief card points at. */
fun NavHostController.openCard(card: Card) {
    val route = when (card.target) {
        "contact" -> if (card.refId > 0) Routes.contact(card.refId) else Routes.PEOPLE
        "document" -> if (card.refId > 0) Routes.document(card.refId) else Routes.STUDIO
        "approvals" -> Routes.APPROVALS
        "ground" -> Routes.GROUND
        "vault" -> Routes.VAULT
        "compass" -> Routes.COMPASS
        "field" -> Routes.FIELD
        "gym" -> Routes.GYM
        "path" -> Routes.PATH
        "hq" -> Routes.HQ
        "network" -> Routes.PEOPLE
        "studio" -> Routes.STUDIO
        "briefing" -> Routes.BRIEFING
        "home" -> Routes.HOME
        "settings" -> Routes.SETTINGS
        else -> null
    } ?: return
    navigate(route) { launchSingleTop = true }
}

/** Opens a module by the short name tasks use. */
fun NavHostController.openModule(module: String) {
    val route = when (module) {
        "compass" -> Routes.COMPASS; "ground" -> Routes.GROUND; "field" -> Routes.FIELD; "network" -> Routes.PEOPLE; "gym" -> Routes.GYM
        "studio" -> Routes.STUDIO; "vault" -> Routes.VAULT; "hq" -> Routes.HQ; "path" -> Routes.PATH; "briefing" -> Routes.BRIEFING
        "review" -> Routes.REVIEW
        else -> Routes.CHIEF
    }
    navigate(route) { launchSingleTop = true }
}

@Composable
fun BallotRoot() {
    val g = LocalContext.current.graph
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { g.case.profile(); loaded = true }
    if (!loaded) return
    val p = profile
    if (p == null || !p.setupDone) {
        SetupScreen()
        return
    }
    val nav = rememberNavController()
    val c = LocalBallotColors.current
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.CHIEF) { ChiefScreen(nav) }
                composable(Routes.PATH) { PathScreen(nav) }
                composable(Routes.PEOPLE) { PeopleScreen(nav) }
                composable(Routes.MORE) { MoreScreen(nav) }
                composable(Routes.APPROVALS) { ApprovalsScreen(nav) }
                composable(Routes.GROUND) { GroundScreen(nav) }
                composable(Routes.BRIEFING) { BriefingScreen(nav) }
                composable(Routes.FIELD) { FieldScreen(nav) }
                composable(Routes.COMPASS) { CompassScreen(nav) }
                composable(Routes.GYM) { GymScreen(nav) }
                composable(Routes.STUDIO) { StudioScreen(nav) }
                composable(Routes.VAULT) { VaultScreen(nav) }
                composable(Routes.HQ) { HqScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.CASEFILE) { CaseFileScreen(nav) }
                composable(Routes.QUIZ) { QuizScreen(nav) }
                composable(Routes.GONOGO) { GoNoGoScreen(nav) }
                composable(Routes.REVIEW) { WeeklyReviewScreen(nav) }
                composable(Routes.CONTACT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ContactScreen(nav, it.arguments?.getLong("id") ?: 0)
                }
                composable(Routes.DOCUMENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    DocumentScreen(nav, it.arguments?.getLong("id") ?: 0)
                }
                composable(Routes.ROOM, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    MockRoomScreen(nav, it.arguments?.getString("id") ?: "door")
                }
            }
        }
        ThickRule(thickness = 2.dp)
        Row(Modifier.fillMaxWidth().background(c.bg).height(64.dp)) {
            tabs.forEach { t ->
                val on = current == t.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(if (on) c.signal else c.bg)
                        .semantics { role = Role.Tab; selected = on; contentDescription = t.label }
                        .clickable {
                            nav.navigate(t.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (on) c.bg else c.fg)
                    Text(t.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (on) c.bg else c.fg)
                }
            }
        }
    }
}

/** App lock with biometric or PIN. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val activity = ctx as? FragmentActivity
    val canBio = remember {
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    fun bio() {
        if (activity == null || !canBio) return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Ballot").setNegativeButtonText("Use PIN").build())
    }
    LaunchedEffect(Unit) { bio() }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("BALLOT", style = MaterialTheme.typography.displayLarge, color = LocalBallotColors.current.signal)
        Small("Locked. Your case file is encrypted on this phone.")
        Gap(24.dp)
        Field(pin, { pin = it.filter { ch -> ch.isDigit() }.take(12); error = "" }, "PIN", singleLine = true, number = true)
        if (error.isNotEmpty()) Body(error, color = LocalBallotColors.current.signal)
        Gap()
        PosterButton("Unlock", onClick = {
            if (g.secrets.checkPin(pin)) onUnlocked() else { error = "Wrong PIN."; pin = "" }
        }, modifier = Modifier.fillMaxWidth())
        if (canBio) {
            Gap()
            PosterButton("Use fingerprint or face", onClick = { bio() }, modifier = Modifier.fillMaxWidth(), primary = false)
        }
    }
}
