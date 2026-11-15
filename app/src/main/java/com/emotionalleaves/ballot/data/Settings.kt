package com.emotionalleaves.ballot.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Routine action kinds the autonomy dial can move between "does without asking" and "asks first". */
object RoutineKind {
    const val RESEARCH = "RESEARCH"
    const val DRAFT = "DRAFT"
    const val REMINDER = "REMINDER"
    const val TRACKER = "TRACKER"
    const val FORM_PREP = "FORM_PREP"
    val all = listOf(RESEARCH, DRAFT, REMINDER, TRACKER, FORM_PREP)
    fun label(k: String) = when (k) {
        RESEARCH -> "Research"
        DRAFT -> "Drafting"
        REMINDER -> "Scheduling reminders"
        TRACKER -> "Updating trackers"
        FORM_PREP -> "Preparing forms"
        else -> k
    }
}

data class AppSettings(
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-5-5",
    val fallbackModel: String = "claude-haiku-5-5",
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val monthlyCapCents: Int = 1500,
    val inputMicrosPerMTok: Long = 3_000_000,
    val outputMicrosPerMTok: Long = 15_000_000,
    val searchMicrosEach: Long = 10_000,
    val askFirst: Set<String> = emptySet(),
    val sleepStartMin: Int = 21 * 60 + 30,
    val sleepEndMin: Int = 6 * 60,
    val darkTheme: Boolean = true,
    val officialDomains: Set<String> = emptySet(),
    val appLock: Boolean = false,
    val autoLockMinutes: Int = 5,
    val coolingMinutes: Int = 60,
    val freshnessDaysRules: Int = 7,
    val freshnessDaysGeneral: Int = 30,
    val safetyMarginPct: Int = 10,
    val supportRatePct: Int = 50,
    val contactsPerHour: Int = 8,
    val volunteerHoursPerWeek: Int = 0,
    val briefMinuteOfDay: Int = 16 * 60 + 15,
    val lastSnapshotDay: Long = 0,
    val textScale: Float = 1f,
)

class SettingsStore(private val context: Context) {
    private object K {
        val provider = stringPreferencesKey("provider")
        val model = stringPreferencesKey("model")
        val fallback = stringPreferencesKey("fallback")
        val baseUrl = stringPreferencesKey("openai_base")
        val cap = intPreferencesKey("cap_cents")
        val inRate = longPreferencesKey("in_rate")
        val outRate = longPreferencesKey("out_rate")
        val searchRate = longPreferencesKey("search_rate")
        val askFirst = stringSetPreferencesKey("ask_first")
        val sleepStart = intPreferencesKey("sleep_start")
        val sleepEnd = intPreferencesKey("sleep_end")
        val dark = booleanPreferencesKey("dark")
        val domains = stringSetPreferencesKey("official_domains")
        val lock = booleanPreferencesKey("app_lock")
        val autoLock = intPreferencesKey("auto_lock")
        val cooling = intPreferencesKey("cooling")
        val freshRules = intPreferencesKey("fresh_rules")
        val freshGeneral = intPreferencesKey("fresh_general")
        val margin = intPreferencesKey("margin")
        val support = intPreferencesKey("support_rate")
        val cph = intPreferencesKey("contacts_per_hour")
        val volHours = intPreferencesKey("vol_hours")
        val briefMin = intPreferencesKey("brief_min")
        val snapDay = longPreferencesKey("snap_day")
        val textScale = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            provider = p[K.provider] ?: d.provider,
            model = p[K.model] ?: d.model,
            fallbackModel = p[K.fallback] ?: d.fallbackModel,
            openAiBaseUrl = p[K.baseUrl] ?: d.openAiBaseUrl,
            monthlyCapCents = p[K.cap] ?: d.monthlyCapCents,
            inputMicrosPerMTok = p[K.inRate] ?: d.inputMicrosPerMTok,
            outputMicrosPerMTok = p[K.outRate] ?: d.outputMicrosPerMTok,
            searchMicrosEach = p[K.searchRate] ?: d.searchMicrosEach,
            askFirst = p[K.askFirst] ?: d.askFirst,
            sleepStartMin = p[K.sleepStart] ?: d.sleepStartMin,
            sleepEndMin = p[K.sleepEnd] ?: d.sleepEndMin,
            darkTheme = p[K.dark] ?: d.darkTheme,
            officialDomains = p[K.domains] ?: d.officialDomains,
            appLock = p[K.lock] ?: d.appLock,
            autoLockMinutes = p[K.autoLock] ?: d.autoLockMinutes,
            coolingMinutes = p[K.cooling] ?: d.coolingMinutes,
            freshnessDaysRules = p[K.freshRules] ?: d.freshnessDaysRules,
            freshnessDaysGeneral = p[K.freshGeneral] ?: d.freshnessDaysGeneral,
            safetyMarginPct = p[K.margin] ?: d.safetyMarginPct,
            supportRatePct = p[K.support] ?: d.supportRatePct,
            contactsPerHour = p[K.cph] ?: d.contactsPerHour,
            volunteerHoursPerWeek = p[K.volHours] ?: d.volunteerHoursPerWeek,
            briefMinuteOfDay = p[K.briefMin] ?: d.briefMinuteOfDay,
            lastSnapshotDay = p[K.snapDay] ?: d.lastSnapshotDay,
            textScale = p[K.textScale] ?: d.textScale,
        )
    }

    suspend fun get(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(get())
        context.dataStore.edit { p ->
            p[K.provider] = n.provider; p[K.model] = n.model; p[K.fallback] = n.fallbackModel
            p[K.baseUrl] = n.openAiBaseUrl; p[K.cap] = n.monthlyCapCents; p[K.inRate] = n.inputMicrosPerMTok
            p[K.outRate] = n.outputMicrosPerMTok; p[K.searchRate] = n.searchMicrosEach; p[K.askFirst] = n.askFirst
            p[K.sleepStart] = n.sleepStartMin; p[K.sleepEnd] = n.sleepEndMin; p[K.dark] = n.darkTheme
            p[K.domains] = n.officialDomains; p[K.lock] = n.appLock; p[K.autoLock] = n.autoLockMinutes
            p[K.cooling] = n.coolingMinutes; p[K.freshRules] = n.freshnessDaysRules; p[K.freshGeneral] = n.freshnessDaysGeneral
            p[K.margin] = n.safetyMarginPct; p[K.support] = n.supportRatePct; p[K.cph] = n.contactsPerHour
            p[K.volHours] = n.volunteerHoursPerWeek; p[K.briefMin] = n.briefMinuteOfDay; p[K.snapDay] = n.lastSnapshotDay
            p[K.textScale] = n.textScale
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

/** API keys, the database key and the PIN hash live in Keystore-backed encrypted storage, never in the repo. */
class SecretStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ballot_secrets", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var anthropicKey: String
        get() = prefs.getString("anthropic_key", "") ?: ""
        set(v) = prefs.edit().putString("anthropic_key", v.trim()).apply()

    var openAiKey: String
        get() = prefs.getString("openai_key", "") ?: ""
        set(v) = prefs.edit().putString("openai_key", v.trim()).apply()

    fun keyFor(provider: String): String = if (provider == "openai") openAiKey else anthropicKey

    fun databasePassphrase(): ByteArray {
        val existing = prefs.getString("db_key", null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString("db_key", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        return bytes
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .apply()
    }

    fun hasPin(): Boolean = prefs.contains("pin_hash")

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        return hash(pin, Base64.decode(salt, Base64.NO_WRAP)) == prefs.getString("pin_hash", null)
    }

    fun clearPin() = prefs.edit().remove("pin_hash").remove("pin_salt").apply()

    fun wipe() {
        prefs.edit().clear().commit()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        var h = salt + pin.toByteArray()
        repeat(10_000) { h = md.digest(h) }
        return Base64.encodeToString(h, Base64.NO_WRAP)
    }
}
