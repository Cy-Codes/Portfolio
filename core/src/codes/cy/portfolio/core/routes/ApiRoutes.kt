package codes.cy.portfolio.core.routes

import codes.cy.portfolio.core.pagination.QueryOptions
import io.ktor.resources.Resource

@Resource("/health")
class HealthRoute {
    @Resource("ready")
    class Ready(val parent: HealthRoute = HealthRoute())
}

@Resource("/read/{slug}")
class ReadWriteupPageRoute(val slug: String)

@Resource("/api/v1/public")
class PublicApi {
    @Resource("portfolio")
    class Portfolio(val parent: PublicApi = PublicApi())

    @Resource("projects")
    class Projects(
        val parent: PublicApi = PublicApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
        val sortBy: String? = null,
        val sortDirection: String? = null,
        val featured: Boolean? = null,
        val skill: String? = null,
    ) {
        @Resource("{slug}")
        class BySlug(
            val parent: Projects = Projects(),
            val slug: String,
        )
    }

    @Resource("writeups")
    class Writeups(
        val parent: PublicApi = PublicApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
        val sortBy: String? = null,
        val sortDirection: String? = null,
        val tag: String? = null,
        val q: String? = null,
    ) {
        @Resource("{slug}")
        class BySlug(
            val parent: Writeups = Writeups(),
            val slug: String,
        )
    }

    @Resource("documents")
    class Documents(
        val parent: PublicApi = PublicApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
    ) {
        @Resource("{id}/download")
        class Download(
            val parent: Documents = Documents(),
            val id: String,
        )
    }

    @Resource("events")
    class Events(val parent: PublicApi = PublicApi())
}

@Resource("/api/v1/admin")
class AdminApi {
    @Resource("auth/session")
    class AuthSession(val parent: AdminApi = AdminApi())

    @Resource("profile")
    class Profile(val parent: AdminApi = AdminApi())

    @Resource("experiences")
    class Experiences(val parent: AdminApi = AdminApi()) {
        @Resource("{id}")
        class ById(
            val parent: Experiences = Experiences(),
            val id: String,
        )
    }

    @Resource("education")
    class Education(val parent: AdminApi = AdminApi()) {
        @Resource("{id}")
        class ById(
            val parent: Education = Education(),
            val id: String,
        )
    }

    @Resource("certifications")
    class Certifications(val parent: AdminApi = AdminApi()) {
        @Resource("{id}")
        class ById(
            val parent: Certifications = Certifications(),
            val id: String,
        )
    }

    @Resource("skills")
    class Skills(val parent: AdminApi = AdminApi()) {
        @Resource("{id}")
        class ById(
            val parent: Skills = Skills(),
            val id: String,
        )
    }

    @Resource("projects")
    class Projects(
        val parent: AdminApi = AdminApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
    ) {
        @Resource("{id}")
        class ById(
            val parent: Projects = Projects(),
            val id: String,
        )
    }

    @Resource("writeups")
    class Writeups(
        val parent: AdminApi = AdminApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
    ) {
        @Resource("{id}")
        class ById(
            val parent: Writeups = Writeups(),
            val id: String,
        )
    }

    @Resource("documents")
    class Documents(
        val parent: AdminApi = AdminApi(),
        val page: Int = QueryOptions.DEFAULT_PAGE,
        val pageSize: Int = QueryOptions.DEFAULT_PAGE_SIZE,
    ) {
        @Resource("presign")
        class Presign(val parent: Documents = Documents())

        @Resource("confirm")
        class Confirm(val parent: Documents = Documents())

        @Resource("{id}")
        class ById(
            val parent: Documents = Documents(),
            val id: String,
        )
    }

    @Resource("stats/overview")
    class StatsOverview(
        val parent: AdminApi = AdminApi(),
        val range: String? = null,
    )
}
