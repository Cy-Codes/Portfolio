package codes.cy.portfolio.admin.navigation

import codes.cy.portfolio.admin.features.auth.AdminAuthAction
import codes.cy.portfolio.admin.features.auth.AdminAuthIntent
import codes.cy.portfolio.admin.features.auth.AdminAuthState
import codes.cy.portfolio.admin.features.auth.AdminAuthStoreFactory
import codes.cy.portfolio.admin.plugins.asAdminDecomposeValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope

@OptIn(ExperimentalDecomposeApi::class)
interface AdminRootComponent : WebNavigationOwner {
    sealed interface Child {
        data object SignIn : Child
        data class Stats(val component: AdminStatsComponent) : Child
        data class Career(val component: AdminCareerComponent) : Child
        data class Projects(val component: AdminProjectsComponent) : Child
        data class Writeups(val component: AdminWriteupsComponent) : Child
        data class Documents(val component: AdminDocumentsComponent) : Child
    }

    val authState: Value<AdminAuthState>
    val childStack: Value<ChildStack<AdminConfig, Child>>
    val activeSection: Value<AdminSection>

    fun onAuthIntent(intent: AdminAuthIntent)
    fun selectSection(section: AdminSection)
    fun signOut()
}

@OptIn(ExperimentalDecomposeApi::class)
class DefaultAdminRootComponent(
    componentContext: ComponentContext,
    private val dependencies: AdminDependencies,
    initialSection: AdminSection = AdminSection.STATS,
) : AdminRootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<AdminConfig>()
    private val storeScope = coroutineScope(dependencies.mainContext)
    private var pendingSection: AdminSection = initialSection

    private val authStore = AdminAuthStoreFactory.create(
        queryApi = dependencies.queryApi,
        configHolder = dependencies.configHolder,
        initialMode = dependencies.initialMode,
        initialToken = dependencies.initialToken,
        autoVerifyInitialToken = dependencies.autoVerifyInitialToken,
    ).apply {
        start(storeScope)
    }

    override val authState: Value<AdminAuthState> = authStore.asAdminDecomposeValue(
        initialState = AdminAuthState(
            baseUrl = dependencies.configHolder.baseUrl,
            mode = dependencies.initialMode,
            tokenInput = dependencies.initialToken,
        ),
        lifecycle = lifecycle,
        coroutineContext = dependencies.mainContext,
        onAction = { action ->
            when (action) {
                is AdminAuthAction.Authenticated -> {
                    navigation.replaceAll(pendingSection.toConfig())
                }

                is AdminAuthAction.SignedOut -> {
                    navigation.replaceAll(AdminConfig.SignIn)
                }
            }
        },
    )

    override val childStack: Value<ChildStack<AdminConfig, AdminRootComponent.Child>> = childStack(
        source = navigation,
        serializer = AdminConfig.serializer(),
        initialConfiguration = AdminConfig.SignIn,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    override val activeSection: Value<AdminSection> =
        childStack.map { stack -> AdminDeepLink.toSectionOrNull(stack.active.configuration) ?: pendingSection }

    override val webNavigation: WebNavigation<*> = childStackWebNavigation(
        navigator = navigation,
        stack = childStack,
        serializer = AdminConfig.serializer(),
        enableHistory = true,
        pathMapper = { child -> AdminDeepLink.toWebPath(child.configuration) },
    )

    private fun createChild(
        config: AdminConfig,
        childContext: ComponentContext,
    ): AdminRootComponent.Child =
        when (config) {
            is AdminConfig.SignIn -> AdminRootComponent.Child.SignIn
            is AdminConfig.Stats -> AdminRootComponent.Child.Stats(
                component = DefaultAdminStatsComponent(childContext, dependencies),
            )
            is AdminConfig.Career -> AdminRootComponent.Child.Career(
                component = DefaultAdminCareerComponent(childContext, dependencies),
            )
            is AdminConfig.Projects -> AdminRootComponent.Child.Projects(
                component = DefaultAdminProjectsComponent(childContext, dependencies),
            )
            is AdminConfig.Writeups -> AdminRootComponent.Child.Writeups(
                component = DefaultAdminWriteupsComponent(childContext, dependencies),
            )
            is AdminConfig.Documents -> AdminRootComponent.Child.Documents(
                component = DefaultAdminDocumentsComponent(childContext, dependencies),
            )
        }

    override fun onAuthIntent(intent: AdminAuthIntent) {
        authStore.intent(intent)
    }

    override fun selectSection(section: AdminSection) {
        pendingSection = section
        if (!authState.value.isAuthenticated) {
            return
        }
        val targetConfig = section.toConfig()
        val existingConfig = childStack.value.items
            .firstOrNull { it.configuration == targetConfig }
            ?.configuration
        navigation.bringToFront(existingConfig ?: targetConfig)
    }

    override fun signOut() {
        authStore.intent(AdminAuthIntent.SignOut)
    }

    companion object {
        fun fromDeepLinkPath(
            componentContext: ComponentContext,
            dependencies: AdminDependencies,
            initialPath: String?,
        ): DefaultAdminRootComponent = DefaultAdminRootComponent(
            componentContext = componentContext,
            dependencies = dependencies,
            initialSection = AdminDeepLink.sectionFromPath(initialPath),
        )
    }
}
