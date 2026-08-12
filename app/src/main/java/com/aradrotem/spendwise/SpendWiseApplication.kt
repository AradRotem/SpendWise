package com.aradrotem.spendwise

import android.app.Application
import com.aradrotem.spendwise.data.auth.AuthRepository
import com.aradrotem.spendwise.data.auth.AuthSessionCoordinator
import com.aradrotem.spendwise.data.auth.FirebaseAuthRepository
import com.aradrotem.spendwise.data.auth.FirebaseAvailability
import com.aradrotem.spendwise.data.auth.FirebaseUnavailableAuthRepository
import com.aradrotem.spendwise.data.auth.FirebaseUnavailableUserProfileRepository
import com.aradrotem.spendwise.data.auth.FirestoreUserProfileRepository
import com.aradrotem.spendwise.data.auth.UserProfileRepository
import com.aradrotem.spendwise.data.local.SpendWiseDatabase
import com.aradrotem.spendwise.data.receipt.FirebaseReceiptStorageRepository
import com.aradrotem.spendwise.data.receipt.FirebaseUnavailableReceiptStorageRepository
import com.aradrotem.spendwise.data.receipt.ReceiptStorageRepository
import com.aradrotem.spendwise.data.sync.ConnectivityObserver
import com.aradrotem.spendwise.data.sync.FirebaseFirestoreSyncClient
import com.aradrotem.spendwise.data.sync.LegacyImportManager
import com.aradrotem.spendwise.data.sync.SharedPrefsSyncWatermarkStore
import com.aradrotem.spendwise.data.sync.SyncEngine
import com.aradrotem.spendwise.data.sync.SyncIdResolver
import com.aradrotem.spendwise.data.sync.adapters.BudgetSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.CategorySyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.GroupExpenseShareSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.GroupExpenseSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.GroupMemberSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.GroupSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.RecurringExceptionSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.RecurringPlanSyncAdapter
import com.aradrotem.spendwise.data.sync.adapters.TransactionSyncAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.aradrotem.spendwise.data.repository.BudgetRepository
import com.aradrotem.spendwise.data.repository.CategoryRepository
import com.aradrotem.spendwise.data.repository.GroupExpenseRepository
import com.aradrotem.spendwise.data.repository.ReceiptRepository
import com.aradrotem.spendwise.data.repository.RecurringOccurrenceExceptionRepository
import com.aradrotem.spendwise.data.repository.RecurringPaymentRepository
import com.aradrotem.spendwise.data.repository.TransactionRepository
import com.aradrotem.spendwise.domain.RecurringOccurrenceManager
import com.aradrotem.spendwise.domain.RecurringPaymentGenerator
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

// Every repository/domain-service that is scoped to one account's Room database. Rebuilt as a
// whole whenever the active account changes (see SpendWiseApplication.rebuildForUid) instead of
// each repository being an independent lazy val, so switching accounts can never leave a stale
// reference to another account's database alive.
class RepositoryBundle(database: SpendWiseDatabase, context: android.content.Context, uid: String?) {
    private val syncMetadataDao = database.syncMetadataDao()

    val transactionRepository: TransactionRepository =
        TransactionRepository(database.transactionDao(), syncMetadataDao, database.receiptPendingDeletionDao())
    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao(), syncMetadataDao)
    val budgetRepository: BudgetRepository = BudgetRepository(database.budgetDao(), syncMetadataDao)
    val recurringPaymentRepository: RecurringPaymentRepository =
        RecurringPaymentRepository(database.recurringPaymentPlanDao(), syncMetadataDao)
    val recurringOccurrenceExceptionRepository: RecurringOccurrenceExceptionRepository =
        RecurringOccurrenceExceptionRepository(database.recurringOccurrenceExceptionDao(), syncMetadataDao)
    val recurringPaymentGenerator: RecurringPaymentGenerator =
        RecurringPaymentGenerator(recurringPaymentRepository, transactionRepository, recurringOccurrenceExceptionRepository)
    val recurringOccurrenceManager: RecurringOccurrenceManager =
        RecurringOccurrenceManager(transactionRepository, recurringPaymentRepository, recurringOccurrenceExceptionRepository)
    val groupExpenseRepository: GroupExpenseRepository = GroupExpenseRepository(
        database.expenseGroupDao(), database.groupMemberDao(), database.groupExpenseDao(), syncMetadataDao
    )

    // Only meaningful for a real signed-in account with Firebase configured - null for the
    // legacy/pre-auth database and for builds without google-services.json (see
    // FirebaseAvailability). Screens/Account UI must treat a null engine as "sync unavailable".
    val syncEngine: SyncEngine? = if (uid != null && FirebaseAvailability.isConfigured(context)) {
        val resolver = SyncIdResolver(syncMetadataDao)
        val adapters = listOf(
            TransactionSyncAdapter(database.transactionDao(), resolver, uid),
            CategorySyncAdapter(database.categoryDao(), uid),
            BudgetSyncAdapter(database.budgetDao(), uid),
            RecurringPlanSyncAdapter(database.recurringPaymentPlanDao(), uid),
            RecurringExceptionSyncAdapter(database.recurringOccurrenceExceptionDao(), resolver, uid),
            GroupSyncAdapter(database.expenseGroupDao(), uid),
            GroupMemberSyncAdapter(database.groupMemberDao(), resolver, uid),
            GroupExpenseSyncAdapter(database.groupExpenseDao(), resolver, uid),
            GroupExpenseShareSyncAdapter(database.groupExpenseDao(), resolver, uid)
        )
        SyncEngine(
            syncMetadataDao = syncMetadataDao,
            adapters = adapters,
            firestoreClient = FirebaseFirestoreSyncClient(FirebaseFirestore.getInstance()),
            watermarkStore = SharedPrefsSyncWatermarkStore(context, uid)
        )
    } else {
        null
    }

    // Independent of whether Firebase is configured (see FirebaseAvailability) - the fallback
    // makes uploads fail with a clear error rather than crashing, so a receipt can still be
    // attached and viewed locally even in a build/dev environment without Storage set up.
    val receiptStorageRepository: ReceiptStorageRepository = if (FirebaseAvailability.isConfigured(context)) {
        FirebaseReceiptStorageRepository(FirebaseStorage.getInstance())
    } else {
        FirebaseUnavailableReceiptStorageRepository()
    }

    // Null for the legacy/pre-auth database - receipts always belong to an authenticated user
    // (see Step 18 plan), same nullability rule as syncEngine above.
    val receiptRepository: ReceiptRepository? = if (uid != null) {
        ReceiptRepository(transactionRepository, receiptStorageRepository, database.receiptPendingDeletionDao(), uid)
    } else {
        null
    }
}

class SpendWiseApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Emits once whenever the active account's database has been swapped and every existing
    // Activity/ViewModel in the process may be holding stale repository references. MainActivity
    // collects this and calls recreate() - see AuthSessionCoordinator for why a live in-place
    // swap isn't safe with this app's viewModel(factory=...) + saveState navigation pattern.
    val recreateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var currentDbQualifier: String? = null

    lateinit var repositories: RepositoryBundle
        private set

    // Independent of the active per-account database, so these are built once and never swapped -
    // only which uid's data they operate on (via rebuildForUid) changes.
    val authRepository: AuthRepository by lazy {
        if (FirebaseAvailability.isConfigured(this)) {
            FirebaseAuthRepository(FirebaseAuth.getInstance())
        } else {
            FirebaseUnavailableAuthRepository()
        }
    }

    val userProfileRepository: UserProfileRepository by lazy {
        if (FirebaseAvailability.isConfigured(this)) {
            FirestoreUserProfileRepository(FirebaseFirestore.getInstance())
        } else {
            FirebaseUnavailableUserProfileRepository()
        }
    }

    private val legacyImportManager by lazy { LegacyImportManager(this) }

    // Bridges auth state to "which account's database is active" - see AuthSessionCoordinator for
    // the dedup/recreate rules. Kept as a lazy val (not started here) so unit tests constructing
    // SpendWiseApplication-adjacent logic never accidentally touch Firebase.
    private val authSessionCoordinator by lazy {
        AuthSessionCoordinator(
            authRepository = authRepository,
            onNewLogin = { uid -> legacyImportManager.importIfNeeded(uid) },
            onUidChanged = { uid, isInitial -> rebuildForUid(uid, notifyRecreate = !isInitial) }
        )
    }

    private val connectivityObserver by lazy { ConnectivityObserver(this) }

    override fun onCreate() {
        super.onCreate()
        rebuildForUid(null, notifyRecreate = false)
        applicationScope.launch {
            authSessionCoordinator.run()
        }
        applicationScope.launch {
            connectivityObserver.onConnected.collect { triggerSync() }
        }
    }

    // Safe to call whenever - each part no-ops if the active account has no sync engine/receipt
    // repository (legacy database). Used by MainActivity.onResume, connectivity regained, and the
    // manual "Sync now" action in Account settings - the same trigger points cover both Firestore
    // sync and any receipts left pending from an offline attach/replace/remove/delete.
    fun triggerSync() {
        repositories.syncEngine?.let { engine -> applicationScope.launch { engine.sync() } }
        repositories.receiptRepository?.let { receipts ->
            applicationScope.launch {
                receipts.retryPendingUploads()
                receipts.retryPendingDeletions()
            }
        }
    }

    // Rebuilds the repository bundle from the database for [uid] (null = legacy/no-account
    // database). No-ops if [uid] is already the active account, so redundant auth-state
    // emissions (A -> A, null -> null) never trigger a rebuild or an Activity recreate.
    fun rebuildForUid(uid: String?, notifyRecreate: Boolean) {
        if (::repositories.isInitialized && uid == currentDbQualifier) return

        val previousQualifier = currentDbQualifier
        val database = SpendWiseDatabase.getInstance(this, uid)
        repositories = RepositoryBundle(database, this, uid)
        currentDbQualifier = uid

        if (previousQualifier != null && previousQualifier != uid) {
            SpendWiseDatabase.closeInstance(previousQualifier)
        }

        // Safety net for fresh installs: MIGRATION_1_2 seeds built-ins for upgrading users,
        // but a brand-new database has no migration to run, so it needs its own seeding.
        // Idempotent (see CategoryRepository.ensureBuiltInCategoriesSeeded), so safe on every launch.
        applicationScope.launch {
            repositories.categoryRepository.ensureBuiltInCategoriesSeeded()
        }
        // Generation is idempotent and runs off the main thread, so app start is never blocked
        // and re-running it (e.g. after creating a plan) never duplicates transactions.
        applicationScope.launch {
            repositories.recurringPaymentGenerator.generateDuePayments()
        }

        if (notifyRecreate) {
            recreateSignal.tryEmit(Unit)
        }
    }
}
