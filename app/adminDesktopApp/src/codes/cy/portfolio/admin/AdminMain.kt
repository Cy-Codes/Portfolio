package codes.cy.portfolio.admin

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Portfolio Admin") {
        AdminApp()
    }
}
