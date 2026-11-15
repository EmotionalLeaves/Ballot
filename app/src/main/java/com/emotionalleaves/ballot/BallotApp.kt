package com.emotionalleaves.ballot

import android.app.Application
import android.content.Context
import android.content.Intent
import com.emotionalleaves.ballot.agent.Chief
import com.emotionalleaves.ballot.data.BallotDatabase
import com.emotionalleaves.ballot.data.CaseFile
import com.emotionalleaves.ballot.data.CrashReports
import com.emotionalleaves.ballot.data.SecretStore
import com.emotionalleaves.ballot.data.SettingsStore
import com.emotionalleaves.ballot.data.Snapshots
import com.emotionalleaves.ballot.work.Notifications
import com.emotionalleaves.ballot.work.Workers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency graph. One instance of everything, created lazily. */
class AppGraph(val context: Context) {
    val settings = SettingsStore(context)
    val secrets by lazy { SecretStore(context) }
    private var _db: BallotDatabase? = null
    val db: BallotDatabase
        @Synchronized get() = _db ?: BallotDatabase.open(context, secrets.databasePassphrase()).also { _db = it }
    val case by lazy { CaseFile(db, settings) }
    val chief by lazy { Chief(context, case, settings, secrets) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Synchronized
    fun closeDb() {
        _db?.close()
        _db = null
    }

    /** Restores a snapshot and restarts the app so every screen reads the restored file. */
    fun restoreAndRestart(snapshot: File) {
        closeDb()
        Snapshots.restore(context, snapshot)
        restart()
    }

    fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val restart = Intent.makeRestartActivityTask(intent!!.component)
        context.startActivity(restart)
        Runtime.getRuntime().exit(0)
    }

    /** Panic wipe: deletes the database, snapshots, crash files, settings and secrets. */
    fun panicWipe() {
        closeDb()
        context.deleteDatabase(BallotDatabase.NAME)
        Snapshots.dir(context).deleteRecursively()
        CrashReports.dir(context).deleteRecursively()
        File(context.filesDir, "recordings").deleteRecursively()
        File(context.filesDir, "vault").deleteRecursively()
        File(context.filesDir, "datastore").deleteRecursively()
        secrets.wipe()
        context.getSharedPreferences("ballot_secrets", Context.MODE_PRIVATE).edit().clear().commit()
        restart()
    }
}

class BallotApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
        graph = AppGraph(this)
        Notifications.createChannels(this)
        Workers.schedule(this)
        // Requests queued while offline in an earlier session run as soon as there is a connection.
        Workers.drainQueueWhenOnline(this)
        graph.scope.launch { graph.settings.applyCalmThemeOnce() }
    }
}

val Context.graph: AppGraph get() = (applicationContext as BallotApp).graph
