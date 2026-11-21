package codes.cy.portfolio.server.db.repositories

import codes.cy.portfolio.core.model.AnalyticsEventType
import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.ConfirmUploadRequest
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.NamedCountDto
import codes.cy.portfolio.core.model.PathStatsDto
import codes.cy.portfolio.core.model.PortfolioOverviewDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.SortDirection
import codes.cy.portfolio.core.model.StatsOverviewDto
import codes.cy.portfolio.core.model.StatsTimeRange
import codes.cy.portfolio.core.model.TrackEventRequest
import codes.cy.portfolio.core.model.UpdateProfileRequest
import codes.cy.portfolio.core.model.UpsertCertificationRequest
import codes.cy.portfolio.core.model.UpsertEducationRequest
import codes.cy.portfolio.core.model.UpsertExperienceRequest
import codes.cy.portfolio.core.model.UpsertProjectRequest
import codes.cy.portfolio.core.model.UpsertSkillRequest
import codes.cy.portfolio.core.model.UpsertWriteupRequest
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.core.pagination.PagedResult
import codes.cy.portfolio.core.pagination.QueryOptions
import codes.cy.portfolio.core.validation.validateAllowedSortField
import codes.cy.portfolio.server.db.config.DatabaseFactory
import codes.cy.portfolio.server.db.mappers.toCertificationDto
import codes.cy.portfolio.server.db.mappers.toDocumentDto
import codes.cy.portfolio.server.db.mappers.toEducationDto
import codes.cy.portfolio.server.db.mappers.toExperienceDto
import codes.cy.portfolio.server.db.mappers.toJvmLocalDate
import codes.cy.portfolio.server.db.mappers.toProfileDto
import codes.cy.portfolio.server.db.mappers.toProjectDetailDto
import codes.cy.portfolio.server.db.mappers.toProjectSummaryDto
import codes.cy.portfolio.server.db.mappers.toSkillDto
import codes.cy.portfolio.server.db.mappers.toWriteupDetailDto
import codes.cy.portfolio.server.db.mappers.toWriteupSummaryDto
import codes.cy.portfolio.server.db.tables.AnalyticsEventsTable
import codes.cy.portfolio.server.db.tables.CertificationsTable
import codes.cy.portfolio.server.db.tables.DocumentsTable
import codes.cy.portfolio.server.db.tables.EducationTable
import codes.cy.portfolio.server.db.tables.ExperienceSkillsTable
import codes.cy.portfolio.server.db.tables.ExperiencesTable
import codes.cy.portfolio.server.db.tables.ProfileTable
import codes.cy.portfolio.server.db.tables.ProjectSkillsTable
import codes.cy.portfolio.server.db.tables.ProjectsTable
import codes.cy.portfolio.server.db.tables.SkillsTable
import codes.cy.portfolio.server.db.tables.TextArrayContainsOp
import codes.cy.portfolio.server.db.tables.WriteupFullTextSearchOp
import codes.cy.portfolio.server.db.tables.WriteupsTable
import java.sql.Types
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

private fun SortDirection.toExposedSortOrder(): SortOrder =
    when (this) {
        SortDirection.ASC -> SortOrder.ASC
        SortDirection.DESC -> SortOrder.DESC
    }

private fun QueryOptions.requireValidOffset(): Long =
    requireNotNull(calculateOffsetOrNull()) {
        "Pagination parameters out of range: page=$page, pageSize=$pageSize."
    }

private fun String.parseUuid(fieldName: String): Uuid =
    runCatching { Uuid.parse(this) }.getOrElse {
        throw IllegalArgumentException("Invalid UUID for '$fieldName': '$this'.")
    }

interface PortfolioQueryRepository {
    suspend fun getPortfolioOverview(): PortfolioOverviewDto
    suspend fun findProfile(): ProfileDto?
    suspend fun findAllExperiences(): List<ExperienceDto>
    suspend fun findAllEducation(): List<EducationDto>
    suspend fun findAllCertifications(): List<CertificationDto>
    suspend fun findAllSkills(): List<SkillDto>
}

interface PortfolioCommandRepository {
    suspend fun upsertProfile(request: UpdateProfileRequest): ProfileDto
    suspend fun createSkill(request: UpsertSkillRequest): SkillDto
    suspend fun updateSkill(id: Uuid, request: UpsertSkillRequest): SkillDto?
    suspend fun deleteSkill(id: Uuid): Boolean
    suspend fun createExperience(request: UpsertExperienceRequest): ExperienceDto
    suspend fun updateExperience(id: Uuid, request: UpsertExperienceRequest): ExperienceDto?
    suspend fun deleteExperience(id: Uuid): Boolean
    suspend fun createEducation(request: UpsertEducationRequest): EducationDto
    suspend fun updateEducation(id: Uuid, request: UpsertEducationRequest): EducationDto?
    suspend fun deleteEducation(id: Uuid): Boolean
    suspend fun createCertification(request: UpsertCertificationRequest): CertificationDto
    suspend fun updateCertification(id: Uuid, request: UpsertCertificationRequest): CertificationDto?
    suspend fun deleteCertification(id: Uuid): Boolean
}

interface ProjectQueryRepository {
    suspend fun getProjects(options: QueryOptions, featuredOnly: Boolean?, skillName: String?): PagedResult<ProjectSummaryDto>
    suspend fun findProjectBySlug(slug: String): ProjectDetailDto?
    suspend fun findProjectById(id: Uuid): ProjectDetailDto?
}

interface ProjectCommandRepository {
    suspend fun createProject(request: UpsertProjectRequest): ProjectDetailDto
    suspend fun updateProject(id: Uuid, request: UpsertProjectRequest): ProjectDetailDto?
    suspend fun deleteProject(id: Uuid): Boolean
}

interface WriteupQueryRepository {
    suspend fun getWriteups(
        options: QueryOptions,
        publishedOnly: Boolean,
        tag: String?,
        searchQuery: String?,
    ): PagedResult<WriteupSummaryDto>

    suspend fun findWriteupBySlug(slug: String, publishedOnly: Boolean): WriteupDetailDto?
    suspend fun findWriteupById(id: Uuid): WriteupDetailDto?
}

interface WriteupCommandRepository {
    suspend fun createWriteup(request: UpsertWriteupRequest): WriteupDetailDto
    suspend fun updateWriteup(id: Uuid, request: UpsertWriteupRequest): WriteupDetailDto?
    suspend fun deleteWriteup(id: Uuid): Boolean
}

interface DocumentQueryRepository {
    suspend fun getDocuments(options: QueryOptions, publicOnly: Boolean): PagedResult<DocumentDto>
    suspend fun findDocumentById(id: Uuid, publicOnly: Boolean): DocumentDto?
}

interface DocumentCommandRepository {
    suspend fun saveConfirmedDocument(
        id: Uuid,
        sizeBytes: Long,
        mimeType: String,
        request: ConfirmUploadRequest,
    ): DocumentDto

    suspend fun deleteDocument(id: Uuid): DocumentDto?
}

interface AnalyticsQueryRepository {
    suspend fun getStatsOverview(range: StatsTimeRange): StatsOverviewDto
}

interface AnalyticsCommandRepository {
    suspend fun recordEvent(request: TrackEventRequest, visitorHash: String)
}

class ExposedPortfolioRepository(
    private val dbFactory: DatabaseFactory,
    private val clock: Clock = Clock.systemUTC(),
) : PortfolioQueryRepository, PortfolioCommandRepository {

    override suspend fun getPortfolioOverview(): PortfolioOverviewDto = dbFactory.dbQuery {
        PortfolioOverviewDto(
            profile = loadProfile(),
            experiences = loadExperiences(),
            education = loadEducation(),
            certifications = loadCertifications(),
            featuredSkills = loadFeaturedSkills(),
        )
    }

    override suspend fun findProfile(): ProfileDto? = dbFactory.dbQuery { loadProfile() }

    override suspend fun findAllExperiences(): List<ExperienceDto> = dbFactory.dbQuery { loadExperiences() }

    override suspend fun findAllEducation(): List<EducationDto> = dbFactory.dbQuery { loadEducation() }

    override suspend fun findAllCertifications(): List<CertificationDto> = dbFactory.dbQuery { loadCertifications() }

    private fun JdbcTransaction.loadProfile(): ProfileDto? =
        ProfileTable
            .selectAll()
            .where { ProfileTable.id eq ProfileTable.SINGLETON_ID }
            .singleOrNull()
            ?.toProfileDto()

    private fun JdbcTransaction.loadExperiences(): List<ExperienceDto> {
        val experienceRows = ExperiencesTable
            .selectAll()
            .orderBy(ExperiencesTable.sortOrder to SortOrder.ASC, ExperiencesTable.startDate to SortOrder.DESC)
            .toList()
        val experienceIds = experienceRows.map { it[ExperiencesTable.id] }
        val skillsByExperienceId = loadSkillsByExperience(experienceIds)
        return experienceRows.map { row ->
            val expId = row[ExperiencesTable.id]
            row.toExperienceDto(skillsByExperienceId[expId].orEmpty())
        }
    }

    private fun JdbcTransaction.loadEducation(): List<EducationDto> =
        EducationTable
            .selectAll()
            .orderBy(EducationTable.sortOrder to SortOrder.ASC, EducationTable.startDate to SortOrder.DESC)
            .map { it.toEducationDto() }

    private fun JdbcTransaction.loadCertifications(): List<CertificationDto> =
        CertificationsTable
            .selectAll()
            .orderBy(CertificationsTable.sortOrder to SortOrder.ASC, CertificationsTable.issuedDate to SortOrder.DESC)
            .map { it.toCertificationDto() }

    private fun JdbcTransaction.loadFeaturedSkills(): List<SkillDto> =
        SkillsTable
            .selectAll()
            .where { SkillsTable.isFeatured eq true }
            .orderBy(SkillsTable.sortOrder to SortOrder.ASC, SkillsTable.name to SortOrder.ASC)
            .map { it.toSkillDto() }

    override suspend fun findAllSkills(): List<SkillDto> = dbFactory.dbQuery {
        SkillsTable
            .selectAll()
            .orderBy(SkillsTable.sortOrder to SortOrder.ASC, SkillsTable.name to SortOrder.ASC)
            .map { it.toSkillDto() }
    }

    override suspend fun upsertProfile(request: UpdateProfileRequest): ProfileDto = dbFactory.dbQuery {
        val now = OffsetDateTime.now(clock)
        val resumeUuid = request.resumeDocumentId?.parseUuid("resumeDocumentId")
        ProfileTable.upsert(ProfileTable.id) {
            it[id] = ProfileTable.SINGLETON_ID
            it[fullName] = request.fullName
            it[headline] = request.headline
            it[bioMarkdown] = request.bioMarkdown
            it[location] = request.location
            it[availability] = request.availability
            it[socialLinks] = request.socialLinks
            it[resumeDocumentId] = resumeUuid
            it[updatedAt] = now
        }
        ProfileTable
            .selectAll()
            .where { ProfileTable.id eq ProfileTable.SINGLETON_ID }
            .single()
            .toProfileDto()
    }

    override suspend fun createSkill(request: UpsertSkillRequest): SkillDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        SkillsTable.insert {
            it[id] = newId
            it[name] = request.name
            it[category] = request.category
            it[isFeatured] = request.isFeatured
            it[sortOrder] = request.sortOrder
        }
        SkillsTable.selectAll().where { SkillsTable.id eq newId }.single().toSkillDto()
    }

    override suspend fun updateSkill(id: Uuid, request: UpsertSkillRequest): SkillDto? = dbFactory.dbQuery {
        val updated = SkillsTable.update({ SkillsTable.id eq id }) {
            it[name] = request.name
            it[category] = request.category
            it[isFeatured] = request.isFeatured
            it[sortOrder] = request.sortOrder
        }
        if (updated == 0) null else SkillsTable.selectAll().where { SkillsTable.id eq id }.single().toSkillDto()
    }

    override suspend fun deleteSkill(id: Uuid): Boolean = dbFactory.dbQuery {
        SkillsTable.deleteWhere { SkillsTable.id eq id } > 0
    }

    override suspend fun createExperience(request: UpsertExperienceRequest): ExperienceDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        val now = OffsetDateTime.now(clock)
        ExperiencesTable.insert {
            it[id] = newId
            it[company] = request.company
            it[title] = request.title
            it[location] = request.location
            it[startDate] = request.startDate.toJvmLocalDate()
            it[endDate] = request.endDate?.toJvmLocalDate()
            it[isCurrent] = request.isCurrent
            it[highlights] = request.highlights
            it[sortOrder] = request.sortOrder
            it[createdAt] = now
            it[updatedAt] = now
        }
        replaceExperienceSkills(newId, request.skillIds.map { it.parseUuid("skillIds") })
        val skills = loadSkillsByExperience(listOf(newId))[newId].orEmpty()
        ExperiencesTable.selectAll().where { ExperiencesTable.id eq newId }.single().toExperienceDto(skills)
    }

    override suspend fun updateExperience(id: Uuid, request: UpsertExperienceRequest): ExperienceDto? = dbFactory.dbQuery {
        val now = OffsetDateTime.now(clock)
        val updated = ExperiencesTable.update({ ExperiencesTable.id eq id }) {
            it[company] = request.company
            it[title] = request.title
            it[location] = request.location
            it[startDate] = request.startDate.toJvmLocalDate()
            it[endDate] = request.endDate?.toJvmLocalDate()
            it[isCurrent] = request.isCurrent
            it[highlights] = request.highlights
            it[sortOrder] = request.sortOrder
            it[updatedAt] = now
        }
        if (updated == 0) {
            null
        } else {
            replaceExperienceSkills(id, request.skillIds.map { it.parseUuid("skillIds") })
            val skills = loadSkillsByExperience(listOf(id))[id].orEmpty()
            ExperiencesTable.selectAll().where { ExperiencesTable.id eq id }.single().toExperienceDto(skills)
        }
    }

    override suspend fun deleteExperience(id: Uuid): Boolean = dbFactory.dbQuery {
        ExperiencesTable.deleteWhere { ExperiencesTable.id eq id } > 0
    }

    override suspend fun createEducation(request: UpsertEducationRequest): EducationDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        EducationTable.insert {
            it[id] = newId
            it[institution] = request.institution
            it[degree] = request.degree
            it[fieldOfStudy] = request.fieldOfStudy
            it[startDate] = request.startDate.toJvmLocalDate()
            it[endDate] = request.endDate?.toJvmLocalDate()
            it[sortOrder] = request.sortOrder
        }
        EducationTable.selectAll().where { EducationTable.id eq newId }.single().toEducationDto()
    }

    override suspend fun updateEducation(id: Uuid, request: UpsertEducationRequest): EducationDto? = dbFactory.dbQuery {
        val updated = EducationTable.update({ EducationTable.id eq id }) {
            it[institution] = request.institution
            it[degree] = request.degree
            it[fieldOfStudy] = request.fieldOfStudy
            it[startDate] = request.startDate.toJvmLocalDate()
            it[endDate] = request.endDate?.toJvmLocalDate()
            it[sortOrder] = request.sortOrder
        }
        if (updated == 0) null else EducationTable.selectAll().where { EducationTable.id eq id }.single().toEducationDto()
    }

    override suspend fun deleteEducation(id: Uuid): Boolean = dbFactory.dbQuery {
        EducationTable.deleteWhere { EducationTable.id eq id } > 0
    }

    override suspend fun createCertification(request: UpsertCertificationRequest): CertificationDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        CertificationsTable.insert {
            it[id] = newId
            it[name] = request.name
            it[issuer] = request.issuer
            it[issuedDate] = request.issuedDate.toJvmLocalDate()
            it[expiresDate] = request.expiresDate?.toJvmLocalDate()
            it[credentialUrl] = request.credentialUrl
            it[sortOrder] = request.sortOrder
        }
        CertificationsTable.selectAll().where { CertificationsTable.id eq newId }.single().toCertificationDto()
    }

    override suspend fun updateCertification(id: Uuid, request: UpsertCertificationRequest): CertificationDto? = dbFactory.dbQuery {
        val updated = CertificationsTable.update({ CertificationsTable.id eq id }) {
            it[name] = request.name
            it[issuer] = request.issuer
            it[issuedDate] = request.issuedDate.toJvmLocalDate()
            it[expiresDate] = request.expiresDate?.toJvmLocalDate()
            it[credentialUrl] = request.credentialUrl
            it[sortOrder] = request.sortOrder
        }
        if (updated == 0) null else CertificationsTable.selectAll().where { CertificationsTable.id eq id }.single().toCertificationDto()
    }

    override suspend fun deleteCertification(id: Uuid): Boolean = dbFactory.dbQuery {
        CertificationsTable.deleteWhere { CertificationsTable.id eq id } > 0
    }

    private fun JdbcTransaction.replaceExperienceSkills(experienceId: Uuid, skillIds: List<Uuid>) {
        ExperienceSkillsTable.deleteWhere { ExperienceSkillsTable.experienceId eq experienceId }
        if (skillIds.isNotEmpty()) {
            ExperienceSkillsTable.batchInsert(skillIds.distinct()) { skillUuid ->
                this[ExperienceSkillsTable.experienceId] = experienceId
                this[ExperienceSkillsTable.skillId] = skillUuid
            }
        }
    }

    private fun JdbcTransaction.loadSkillsByExperience(experienceIds: List<Uuid>): Map<Uuid, List<SkillDto>> {
        if (experienceIds.isEmpty()) return emptyMap()
        return (ExperienceSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { ExperienceSkillsTable.experienceId inList experienceIds }
            .orderBy(SkillsTable.sortOrder to SortOrder.ASC, SkillsTable.name to SortOrder.ASC)
            .groupBy(
                keySelector = { it[ExperienceSkillsTable.experienceId] },
                valueTransform = { it.toSkillDto() },
            )
    }
}

class ExposedProjectRepository(
    private val dbFactory: DatabaseFactory,
    private val clock: Clock = Clock.systemUTC(),
) : ProjectQueryRepository, ProjectCommandRepository {

    override suspend fun getProjects(
        options: QueryOptions,
        featuredOnly: Boolean?,
        skillName: String?,
    ): PagedResult<ProjectSummaryDto> = dbFactory.dbQuery {
        val offset = options.requireValidOffset()
        val sortField = options.sortBy.validateAllowedSortField(ALLOWED_SORT_FIELDS)
        val sortOrder = options.sortDirection.toExposedSortOrder()

        val matchingIdsBySkill: List<Uuid>? = skillName?.trim()?.takeIf { it.isNotEmpty() }?.let { targetSkill ->
            (ProjectSkillsTable innerJoin SkillsTable)
                .select(ProjectSkillsTable.projectId)
                .where { SkillsTable.name eq targetSkill }
                .map { it[ProjectSkillsTable.projectId] }
                .distinct()
        }

        if (matchingIdsBySkill != null && matchingIdsBySkill.isEmpty()) {
            return@dbQuery PagedResult.of(emptyList(), 0L, options)
        }

        val predicates = listOfNotNull(
            featuredOnly?.let { ProjectsTable.isFeatured eq it },
            matchingIdsBySkill?.let { ProjectsTable.id inList it },
        )
        val combinedWhere: Op<Boolean> = predicates.fold(Op.TRUE as Op<Boolean>) { acc, next -> acc and next }

        val totalCount = ProjectsTable.selectAll().where { combinedWhere }.count()
        val sortColumns = when (sortField) {
            "title" -> arrayOf(ProjectsTable.title to sortOrder, ProjectsTable.sortOrder to SortOrder.ASC)
            "updated_at" -> arrayOf(ProjectsTable.updatedAt to sortOrder, ProjectsTable.sortOrder to SortOrder.ASC)
            else -> arrayOf(ProjectsTable.sortOrder to sortOrder, ProjectsTable.updatedAt to SortOrder.DESC)
        }

        val rows = ProjectsTable
            .selectAll()
            .where { combinedWhere }
            .orderBy(*sortColumns)
            .limit(options.normalizedPageSize)
            .offset(offset)
            .toList()

        val skillsMap = loadSkillsByProject(rows.map { it[ProjectsTable.id] })
        val items = rows.map { row -> row.toProjectSummaryDto(skillsMap[row[ProjectsTable.id]].orEmpty()) }
        PagedResult.of(items = items, totalCount = totalCount, options = options)
    }

    override suspend fun findProjectBySlug(slug: String): ProjectDetailDto? = dbFactory.dbQuery {
        val row = ProjectsTable.selectAll().where { ProjectsTable.slug eq slug }.singleOrNull() ?: return@dbQuery null
        val projectId = row[ProjectsTable.id]
        row.toProjectDetailDto(loadSkillsByProject(listOf(projectId))[projectId].orEmpty())
    }

    override suspend fun findProjectById(id: Uuid): ProjectDetailDto? = dbFactory.dbQuery {
        val row = ProjectsTable.selectAll().where { ProjectsTable.id eq id }.singleOrNull() ?: return@dbQuery null
        row.toProjectDetailDto(loadSkillsByProject(listOf(id))[id].orEmpty())
    }

    override suspend fun createProject(request: UpsertProjectRequest): ProjectDetailDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        val now = OffsetDateTime.now(clock)
        ProjectsTable.insert {
            it[id] = newId
            it[slug] = request.slug
            it[title] = request.title
            it[summary] = request.summary
            it[caseStudyMarkdown] = request.caseStudyMarkdown
            it[repoUrl] = request.repoUrl
            it[liveUrl] = request.liveUrl
            it[coverDocumentId] = request.coverDocumentId?.parseUuid("coverDocumentId")
            it[status] = request.status
            it[isFeatured] = request.isFeatured
            it[sortOrder] = request.sortOrder
            it[createdAt] = now
            it[updatedAt] = now
        }
        replaceProjectSkills(newId, request.skillIds.map { it.parseUuid("skillIds") })
        val skills = loadSkillsByProject(listOf(newId))[newId].orEmpty()
        ProjectsTable.selectAll().where { ProjectsTable.id eq newId }.single().toProjectDetailDto(skills)
    }

    override suspend fun updateProject(id: Uuid, request: UpsertProjectRequest): ProjectDetailDto? = dbFactory.dbQuery {
        val now = OffsetDateTime.now(clock)
        val updated = ProjectsTable.update({ ProjectsTable.id eq id }) {
            it[slug] = request.slug
            it[title] = request.title
            it[summary] = request.summary
            it[caseStudyMarkdown] = request.caseStudyMarkdown
            it[repoUrl] = request.repoUrl
            it[liveUrl] = request.liveUrl
            it[coverDocumentId] = request.coverDocumentId?.parseUuid("coverDocumentId")
            it[status] = request.status
            it[isFeatured] = request.isFeatured
            it[sortOrder] = request.sortOrder
            it[updatedAt] = now
        }
        if (updated == 0) {
            null
        } else {
            replaceProjectSkills(id, request.skillIds.map { it.parseUuid("skillIds") })
            val skills = loadSkillsByProject(listOf(id))[id].orEmpty()
            ProjectsTable.selectAll().where { ProjectsTable.id eq id }.single().toProjectDetailDto(skills)
        }
    }

    override suspend fun deleteProject(id: Uuid): Boolean = dbFactory.dbQuery {
        ProjectsTable.deleteWhere { ProjectsTable.id eq id } > 0
    }

    private fun JdbcTransaction.replaceProjectSkills(projectId: Uuid, skillIds: List<Uuid>) {
        ProjectSkillsTable.deleteWhere { ProjectSkillsTable.projectId eq projectId }
        if (skillIds.isNotEmpty()) {
            ProjectSkillsTable.batchInsert(skillIds.distinct()) { skillUuid ->
                this[ProjectSkillsTable.projectId] = projectId
                this[ProjectSkillsTable.skillId] = skillUuid
            }
        }
    }

    private fun JdbcTransaction.loadSkillsByProject(projectIds: List<Uuid>): Map<Uuid, List<SkillDto>> {
        if (projectIds.isEmpty()) return emptyMap()
        return (ProjectSkillsTable innerJoin SkillsTable)
            .selectAll()
            .where { ProjectSkillsTable.projectId inList projectIds }
            .orderBy(SkillsTable.sortOrder to SortOrder.ASC, SkillsTable.name to SortOrder.ASC)
            .groupBy(
                keySelector = { it[ProjectSkillsTable.projectId] },
                valueTransform = { it.toSkillDto() },
            )
    }

    companion object {
        val ALLOWED_SORT_FIELDS: Set<String> = setOf("sort_order", "updated_at", "title")
    }
}

class ExposedWriteupRepository(
    private val dbFactory: DatabaseFactory,
    private val clock: Clock = Clock.systemUTC(),
) : WriteupQueryRepository, WriteupCommandRepository {

    override suspend fun getWriteups(
        options: QueryOptions,
        publishedOnly: Boolean,
        tag: String?,
        searchQuery: String?,
    ): PagedResult<WriteupSummaryDto> = dbFactory.dbQuery {
        val offset = options.requireValidOffset()
        val sortField = options.sortBy.validateAllowedSortField(ALLOWED_SORT_FIELDS)
        val sortOrder = options.sortDirection.toExposedSortOrder()

        val predicates = listOfNotNull(
            if (publishedOnly) WriteupsTable.status eq WriteupStatus.PUBLISHED else null,
            tag?.trim()?.takeIf { it.isNotEmpty() }?.let { TextArrayContainsOp(WriteupsTable.tags, it) },
            searchQuery?.trim()?.takeIf { it.isNotEmpty() }?.let { WriteupFullTextSearchOp(it) },
        )
        val combinedWhere: Op<Boolean> = predicates.fold(Op.TRUE as Op<Boolean>) { acc, next -> acc and next }

        val totalCount = WriteupsTable.selectAll().where { combinedWhere }.count()
        val sortColumns = when (sortField) {
            "title" -> arrayOf(WriteupsTable.title to sortOrder)
            "updated_at" -> arrayOf(WriteupsTable.updatedAt to sortOrder)
            else -> arrayOf(WriteupsTable.publishedAt to sortOrder, WriteupsTable.updatedAt to SortOrder.DESC)
        }

        val items = WriteupsTable
            .selectAll()
            .where { combinedWhere }
            .orderBy(*sortColumns)
            .limit(options.normalizedPageSize)
            .offset(offset)
            .map { it.toWriteupSummaryDto() }

        PagedResult.of(items = items, totalCount = totalCount, options = options)
    }

    override suspend fun findWriteupBySlug(slug: String, publishedOnly: Boolean): WriteupDetailDto? = dbFactory.dbQuery {
        val whereClause = if (publishedOnly) {
            (WriteupsTable.slug eq slug) and (WriteupsTable.status eq WriteupStatus.PUBLISHED)
        } else {
            WriteupsTable.slug eq slug
        }
        WriteupsTable.selectAll().where { whereClause }.singleOrNull()?.toWriteupDetailDto()
    }

    override suspend fun findWriteupById(id: Uuid): WriteupDetailDto? = dbFactory.dbQuery {
        WriteupsTable.selectAll().where { WriteupsTable.id eq id }.singleOrNull()?.toWriteupDetailDto()
    }

    override suspend fun createWriteup(request: UpsertWriteupRequest): WriteupDetailDto = dbFactory.dbQuery {
        val newId = Uuid.random()
        val now = OffsetDateTime.now(clock)
        val resolvedPublishedAt = if (request.status == WriteupStatus.PUBLISHED) now else null
        WriteupsTable.insert {
            it[id] = newId
            it[slug] = request.slug
            it[title] = request.title
            it[subtitle] = request.subtitle
            it[summary] = request.summary
            it[bodyMarkdown] = request.bodyMarkdown
            it[tags] = request.tags
            it[status] = request.status
            it[readingTimeMin] = request.readingTimeMin.coerceAtLeast(MIN_READING_TIME_MIN)
            it[coverDocumentId] = request.coverDocumentId?.parseUuid("coverDocumentId")
            it[publishedAt] = resolvedPublishedAt
            it[createdAt] = now
            it[updatedAt] = now
        }
        WriteupsTable.selectAll().where { WriteupsTable.id eq newId }.single().toWriteupDetailDto()
    }

    override suspend fun updateWriteup(id: Uuid, request: UpsertWriteupRequest): WriteupDetailDto? = dbFactory.dbQuery {
        val existing = WriteupsTable.selectAll().where { WriteupsTable.id eq id }.singleOrNull() ?: return@dbQuery null
        val now = OffsetDateTime.now(clock)
        val existingPublishedAt = existing[WriteupsTable.publishedAt]
        val nextPublishedAt = when {
            request.status == WriteupStatus.PUBLISHED && existingPublishedAt == null -> now
            else -> existingPublishedAt
        }
        WriteupsTable.update({ WriteupsTable.id eq id }) {
            it[slug] = request.slug
            it[title] = request.title
            it[subtitle] = request.subtitle
            it[summary] = request.summary
            it[bodyMarkdown] = request.bodyMarkdown
            it[tags] = request.tags
            it[status] = request.status
            it[readingTimeMin] = request.readingTimeMin.coerceAtLeast(MIN_READING_TIME_MIN)
            it[coverDocumentId] = request.coverDocumentId?.parseUuid("coverDocumentId")
            it[publishedAt] = nextPublishedAt
            it[updatedAt] = now
        }
        WriteupsTable.selectAll().where { WriteupsTable.id eq id }.single().toWriteupDetailDto()
    }

    override suspend fun deleteWriteup(id: Uuid): Boolean = dbFactory.dbQuery {
        WriteupsTable.deleteWhere { WriteupsTable.id eq id } > 0
    }

    companion object {
        private const val MIN_READING_TIME_MIN: Int = 1
        val ALLOWED_SORT_FIELDS: Set<String> = setOf("published_at", "updated_at", "title")
    }
}

class ExposedDocumentRepository(
    private val dbFactory: DatabaseFactory,
    private val clock: Clock = Clock.systemUTC(),
) : DocumentQueryRepository, DocumentCommandRepository {

    override suspend fun getDocuments(options: QueryOptions, publicOnly: Boolean): PagedResult<DocumentDto> = dbFactory.dbQuery {
        val offset = options.requireValidOffset()
        val whereClause: Op<Boolean> = if (publicOnly) {
            DocumentsTable.isPublic eq true
        } else {
            Op.TRUE
        }
        val totalCount = DocumentsTable.selectAll().where { whereClause }.count()
        val items = DocumentsTable
            .selectAll()
            .where { whereClause }
            .orderBy(DocumentsTable.createdAt to SortOrder.DESC)
            .limit(options.normalizedPageSize)
            .offset(offset)
            .map { it.toDocumentDto() }
        PagedResult.of(items = items, totalCount = totalCount, options = options)
    }

    override suspend fun findDocumentById(id: Uuid, publicOnly: Boolean): DocumentDto? = dbFactory.dbQuery {
        val whereClause = if (publicOnly) {
            (DocumentsTable.id eq id) and (DocumentsTable.isPublic eq true)
        } else {
            DocumentsTable.id eq id
        }
        DocumentsTable.selectAll().where { whereClause }.singleOrNull()?.toDocumentDto()
    }

    override suspend fun saveConfirmedDocument(
        id: Uuid,
        sizeBytes: Long,
        mimeType: String,
        request: ConfirmUploadRequest,
    ): DocumentDto = dbFactory.dbQuery {
        val now = OffsetDateTime.now(clock)
        DocumentsTable.insert {
            it[DocumentsTable.id] = id
            it[title] = request.title
            it[category] = request.category
            it[originalFilename] = request.originalFilename
            it[DocumentsTable.mimeType] = mimeType
            it[DocumentsTable.sizeBytes] = sizeBytes
            it[sha256] = request.sha256
            it[storageKey] = request.storageKey
            it[isPublic] = request.isPublic
            it[createdAt] = now
        }
        DocumentsTable.selectAll().where { DocumentsTable.id eq id }.single().toDocumentDto()
    }

    override suspend fun deleteDocument(id: Uuid): DocumentDto? = dbFactory.dbQuery {
        val existing = DocumentsTable.selectAll().where { DocumentsTable.id eq id }.singleOrNull()?.toDocumentDto()
            ?: return@dbQuery null
        DocumentsTable.deleteWhere { DocumentsTable.id eq id }
        existing
    }
}

class ExposedAnalyticsRepository(
    private val dbFactory: DatabaseFactory,
    private val clock: Clock = Clock.systemUTC(),
) : AnalyticsQueryRepository, AnalyticsCommandRepository {

    override suspend fun recordEvent(request: TrackEventRequest, visitorHash: String) {
        val now = OffsetDateTime.now(clock)
        val entityJavaUuid = request.entityId?.parseUuid("entityId")?.let { UUID.fromString(it.toString()) }
        dbFactory.executeUpdateNoReturning(INSERT_EVENT_SQL) { stmt ->
            stmt.setString(1, request.eventType.name)
            stmt.setString(2, request.path)
            if (entityJavaUuid != null) {
                stmt.setObject(3, entityJavaUuid)
            } else {
                stmt.setNull(3, Types.OTHER)
            }
            if (request.referrerHost != null) {
                stmt.setString(4, request.referrerHost)
            } else {
                stmt.setNull(4, Types.VARCHAR)
            }
            stmt.setString(5, request.platform.name)
            stmt.setString(6, request.viewportTier.name)
            stmt.setString(7, visitorHash)
            stmt.setObject(8, now)
        }
    }

    override suspend fun getStatsOverview(range: StatsTimeRange): StatsOverviewDto = dbFactory.dbQuery {
        val cutoff = range.days?.let { OffsetDateTime.now(clock).minusDays(it.toLong()) }
        val rangePredicate: Op<Boolean> = cutoff?.let {
            AnalyticsEventsTable.createdAt greaterEq it
        } ?: Op.TRUE

        val countExpr = AnalyticsEventsTable.id.count()
        val uniqueVisitorsExpr = AnalyticsEventsTable.visitorHash.countDistinct()

        val totalsRow = AnalyticsEventsTable
            .select(countExpr, uniqueVisitorsExpr)
            .where { rangePredicate }
            .single()

        val countsByType = AnalyticsEventsTable
            .select(AnalyticsEventsTable.eventType, countExpr)
            .where { rangePredicate }
            .groupBy(AnalyticsEventsTable.eventType)
            .associate { row -> row[AnalyticsEventsTable.eventType] to row[countExpr] }

        val topPaths = AnalyticsEventsTable
            .select(AnalyticsEventsTable.path, AnalyticsEventsTable.eventType, countExpr, uniqueVisitorsExpr)
            .where { rangePredicate }
            .groupBy(AnalyticsEventsTable.path, AnalyticsEventsTable.eventType)
            .orderBy(countExpr to SortOrder.DESC)
            .limit(TOP_STATS_LIMIT)
            .map { row ->
                PathStatsDto(
                    path = row[AnalyticsEventsTable.path],
                    eventType = row[AnalyticsEventsTable.eventType],
                    eventCount = row[countExpr],
                    uniqueVisitors = row[uniqueVisitorsExpr],
                )
            }

        val topReferrers = AnalyticsEventsTable
            .select(AnalyticsEventsTable.referrerHost, countExpr)
            .where { rangePredicate and AnalyticsEventsTable.referrerHost.isNotNull() }
            .groupBy(AnalyticsEventsTable.referrerHost)
            .orderBy(countExpr to SortOrder.DESC)
            .limit(TOP_STATS_LIMIT)
            .mapNotNull { row ->
                row[AnalyticsEventsTable.referrerHost]?.let { host ->
                    NamedCountDto(name = host, count = row[countExpr])
                }
            }

        val byPlatform = AnalyticsEventsTable
            .select(AnalyticsEventsTable.platform, countExpr)
            .where { rangePredicate }
            .groupBy(AnalyticsEventsTable.platform)
            .orderBy(countExpr to SortOrder.DESC)
            .map { row -> NamedCountDto(name = row[AnalyticsEventsTable.platform].name, count = row[countExpr]) }

        val byViewportTier = AnalyticsEventsTable
            .select(AnalyticsEventsTable.viewportTier, countExpr)
            .where { rangePredicate }
            .groupBy(AnalyticsEventsTable.viewportTier)
            .orderBy(countExpr to SortOrder.DESC)
            .map { row -> NamedCountDto(name = row[AnalyticsEventsTable.viewportTier].name, count = row[countExpr]) }

        StatsOverviewDto(
            range = range,
            totalEvents = totalsRow[countExpr],
            uniqueVisitors = totalsRow[uniqueVisitorsExpr],
            pageViews = countsByType[AnalyticsEventType.PAGE_VIEW] ?: 0L,
            writeupReads = countsByType[AnalyticsEventType.WRITEUP_READ] ?: 0L,
            projectClicks = countsByType[AnalyticsEventType.PROJECT_CLICK] ?: 0L,
            documentDownloads = countsByType[AnalyticsEventType.DOCUMENT_DOWNLOAD] ?: 0L,
            topPaths = topPaths,
            topReferrers = topReferrers,
            byPlatform = byPlatform,
            byViewportTier = byViewportTier,
        )
    }

    companion object {
        private const val TOP_STATS_LIMIT: Int = 10
        private const val INSERT_EVENT_SQL: String =
            """
            INSERT INTO analytics.events (
                event_type, path, entity_id, referrer_host, platform, viewport_tier, visitor_hash, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """
    }
}
