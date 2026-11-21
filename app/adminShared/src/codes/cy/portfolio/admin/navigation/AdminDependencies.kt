package codes.cy.portfolio.admin.navigation

import codes.cy.portfolio.admin.data.AdminAuthCredentials
import codes.cy.portfolio.admin.data.AdminAuthMode
import codes.cy.portfolio.admin.data.AdminCommandApi
import codes.cy.portfolio.admin.data.AdminHttpClientFactory
import codes.cy.portfolio.admin.data.AdminQueryApi
import codes.cy.portfolio.admin.data.AdminSessionConfigHolder
import codes.cy.portfolio.admin.data.KtorAdminApiClient
import codes.cy.portfolio.admin.platform.SelectedUploadFile
import codes.cy.portfolio.admin.platform.pickUploadFile
import io.ktor.client.engine.HttpClientEngine
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

data class AdminDependencies(
    val configHolder: AdminSessionConfigHolder,
    val queryApi: AdminQueryApi,
    val commandApi: AdminCommandApi,
    val initialMode: AdminAuthMode = AdminAuthMode.DEV_BEARER_TOKEN,
    val initialToken: String = "",
    val autoVerifyInitialToken: Boolean = initialToken.isNotBlank(),
    val mainContext: CoroutineContext = Dispatchers.Main.immediate,
    val ioContext: CoroutineContext = Dispatchers.Default,
    val filePicker: suspend () -> SelectedUploadFile? = ::pickUploadFile,
) {
    companion object {
        const val DEFAULT_LOCAL_ADMIN_URL: String = "http://127.0.0.1:8081"

        fun fromBaseUrl(
            baseUrl: String = DEFAULT_LOCAL_ADMIN_URL,
            initialMode: AdminAuthMode = AdminAuthMode.DEV_BEARER_TOKEN,
            initialToken: String = "",
            autoVerifyInitialToken: Boolean = initialToken.isNotBlank(),
            apiEngine: HttpClientEngine? = null,
            uploadEngine: HttpClientEngine? = null,
            mainContext: CoroutineContext = Dispatchers.Main.immediate,
            ioContext: CoroutineContext = Dispatchers.Default,
            filePicker: suspend () -> SelectedUploadFile? = ::pickUploadFile,
        ): AdminDependencies {
            val holder = AdminSessionConfigHolder(
                initialBaseUrl = baseUrl,
                initialCredentials = AdminAuthCredentials(mode = initialMode, token = initialToken),
            )
            val apiHttpClient = AdminHttpClientFactory.createApiClient(configHolder = holder, engine = apiEngine)
            val binaryUploadClient = AdminHttpClientFactory.createBinaryUploadClient(engine = uploadEngine)
            val client = KtorAdminApiClient(
                apiHttpClient = apiHttpClient,
                binaryUploadClient = binaryUploadClient,
            )
            return AdminDependencies(
                configHolder = holder,
                queryApi = client,
                commandApi = client,
                initialMode = initialMode,
                initialToken = initialToken,
                autoVerifyInitialToken = autoVerifyInitialToken,
                mainContext = mainContext,
                ioContext = ioContext,
                filePicker = filePicker,
            )
        }
    }
}
