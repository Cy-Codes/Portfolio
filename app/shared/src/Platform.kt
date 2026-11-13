package codes.cy.portfolio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
