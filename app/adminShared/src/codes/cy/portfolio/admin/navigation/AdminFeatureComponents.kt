package codes.cy.portfolio.admin.navigation

import codes.cy.portfolio.admin.features.career.AdminCareerIntent
import codes.cy.portfolio.admin.features.career.AdminCareerState
import codes.cy.portfolio.admin.features.career.AdminCareerStoreFactory
import codes.cy.portfolio.admin.features.documents.AdminDocumentsIntent
import codes.cy.portfolio.admin.features.documents.AdminDocumentsState
import codes.cy.portfolio.admin.features.documents.AdminDocumentsStoreFactory
import codes.cy.portfolio.admin.features.projects.AdminProjectsIntent
import codes.cy.portfolio.admin.features.projects.AdminProjectsState
import codes.cy.portfolio.admin.features.projects.AdminProjectsStoreFactory
import codes.cy.portfolio.admin.features.stats.AdminStatsIntent
import codes.cy.portfolio.admin.features.stats.AdminStatsState
import codes.cy.portfolio.admin.features.stats.AdminStatsStoreFactory
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsIntent
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsState
import codes.cy.portfolio.admin.features.writeups.AdminWriteupsStoreFactory
import codes.cy.portfolio.admin.plugins.asAdminDecomposeValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope

interface AdminStatsComponent {
    val state: Value<AdminStatsState>
    fun onIntent(intent: AdminStatsIntent)
}

interface AdminCareerComponent {
    val state: Value<AdminCareerState>
    fun onIntent(intent: AdminCareerIntent)
}

interface AdminProjectsComponent {
    val state: Value<AdminProjectsState>
    fun onIntent(intent: AdminProjectsIntent)
}

interface AdminWriteupsComponent {
    val state: Value<AdminWriteupsState>
    fun onIntent(intent: AdminWriteupsIntent)
}

interface AdminDocumentsComponent {
    val state: Value<AdminDocumentsState>
    fun onIntent(intent: AdminDocumentsIntent)
}

class DefaultAdminStatsComponent(
    componentContext: ComponentContext,
    dependencies: AdminDependencies,
) : AdminStatsComponent, ComponentContext by componentContext {
    private val storeScope = coroutineScope(dependencies.mainContext)
    private val store = AdminStatsStoreFactory.create(dependencies.queryApi).apply {
        start(storeScope)
    }

    override val state: Value<AdminStatsState> = store.asAdminDecomposeValue(
        initialState = AdminStatsState(),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
    )

    override fun onIntent(intent: AdminStatsIntent) {
        store.intent(intent)
    }
}

class DefaultAdminCareerComponent(
    componentContext: ComponentContext,
    dependencies: AdminDependencies,
) : AdminCareerComponent, ComponentContext by componentContext {
    private val storeScope = coroutineScope(dependencies.mainContext)
    private val store = AdminCareerStoreFactory.create(
        queryApi = dependencies.queryApi,
        commandApi = dependencies.commandApi,
    ).apply {
        start(storeScope)
    }

    override val state: Value<AdminCareerState> = store.asAdminDecomposeValue(
        initialState = AdminCareerState(),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
    )

    override fun onIntent(intent: AdminCareerIntent) {
        store.intent(intent)
    }
}

class DefaultAdminProjectsComponent(
    componentContext: ComponentContext,
    dependencies: AdminDependencies,
) : AdminProjectsComponent, ComponentContext by componentContext {
    private val storeScope = coroutineScope(dependencies.mainContext)
    private val store = AdminProjectsStoreFactory.create(
        queryApi = dependencies.queryApi,
        commandApi = dependencies.commandApi,
    ).apply {
        start(storeScope)
    }

    override val state: Value<AdminProjectsState> = store.asAdminDecomposeValue(
        initialState = AdminProjectsState(),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
    )

    override fun onIntent(intent: AdminProjectsIntent) {
        store.intent(intent)
    }
}

class DefaultAdminWriteupsComponent(
    componentContext: ComponentContext,
    dependencies: AdminDependencies,
) : AdminWriteupsComponent, ComponentContext by componentContext {
    private val storeScope = coroutineScope(dependencies.mainContext)
    private val store = AdminWriteupsStoreFactory.create(
        queryApi = dependencies.queryApi,
        commandApi = dependencies.commandApi,
    ).apply {
        start(storeScope)
    }

    override val state: Value<AdminWriteupsState> = store.asAdminDecomposeValue(
        initialState = AdminWriteupsState(),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
    )

    override fun onIntent(intent: AdminWriteupsIntent) {
        store.intent(intent)
    }
}

class DefaultAdminDocumentsComponent(
    componentContext: ComponentContext,
    dependencies: AdminDependencies,
) : AdminDocumentsComponent, ComponentContext by componentContext {
    private val storeScope = coroutineScope(dependencies.mainContext)
    private val store = AdminDocumentsStoreFactory.create(
        queryApi = dependencies.queryApi,
        commandApi = dependencies.commandApi,
        filePicker = dependencies.filePicker,
        hashContext = dependencies.ioContext,
    ).apply {
        start(storeScope)
    }

    override val state: Value<AdminDocumentsState> = store.asAdminDecomposeValue(
        initialState = AdminDocumentsState(),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
    )

    override fun onIntent(intent: AdminDocumentsIntent) {
        store.intent(intent)
    }
}
