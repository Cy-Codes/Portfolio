package codes.cy

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform