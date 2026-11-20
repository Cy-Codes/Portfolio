package codes.cy.portfolio.server.db.tables

import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.SocialLinkDto
import codes.cy.portfolio.core.model.WriteupStatus
import codes.cy.portfolio.core.serialization.PortfolioJson
import java.time.OffsetDateTime
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

private const val STATUS_MAX_LENGTH: Int = 32
private const val SHORT_TEXT_MAX_LENGTH: Int = 256
private const val SHA256_LENGTH: Int = 64

object DocumentsTable : Table("portfolio.documents") {
    val id: Column<Uuid> = uuid("id")
    val title: Column<String> = text("title")
    val category: Column<DocumentCategory> = enumerationByName("category", STATUS_MAX_LENGTH, DocumentCategory::class)
    val originalFilename: Column<String> = text("original_filename")
    val mimeType: Column<String> = text("mime_type")
    val sizeBytes: Column<Long> = long("size_bytes")
    val sha256: Column<String> = varchar("sha256", SHA256_LENGTH)
    val storageKey: Column<String> = text("storage_key").uniqueIndex()
    val isPublic: Column<Boolean> = bool("is_public")
    val createdAt: Column<OffsetDateTime> = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object ProfileTable : Table("portfolio.profile") {
    const val SINGLETON_ID: Long = 1L

    val id: Column<Long> = long("id")
    val fullName: Column<String> = text("full_name")
    val headline: Column<String> = text("headline")
    val bioMarkdown: Column<String> = text("bio_markdown")
    val location: Column<String?> = text("location").nullable()
    val availability: Column<String?> = text("availability").nullable()
    val socialLinks: Column<List<SocialLinkDto>> = jsonb("social_links", PortfolioJson)
    val resumeDocumentId: Column<Uuid?> = uuid("resume_document_id")
        .references(DocumentsTable.id)
        .nullable()
    val updatedAt: Column<OffsetDateTime> = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object ExperiencesTable : Table("portfolio.experiences") {
    val id: Column<Uuid> = uuid("id")
    val company: Column<String> = text("company")
    val title: Column<String> = text("title")
    val location: Column<String?> = text("location").nullable()
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val isCurrent: Column<Boolean> = bool("is_current")
    val highlights: Column<List<String>> = jsonb("highlights", PortfolioJson)
    val sortOrder: Column<Int> = integer("sort_order")
    val createdAt: Column<OffsetDateTime> = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt: Column<OffsetDateTime> = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object EducationTable : Table("portfolio.education") {
    val id: Column<Uuid> = uuid("id")
    val institution: Column<String> = text("institution")
    val degree: Column<String> = text("degree")
    val fieldOfStudy: Column<String?> = text("field_of_study").nullable()
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val sortOrder: Column<Int> = integer("sort_order")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object CertificationsTable : Table("portfolio.certifications") {
    val id: Column<Uuid> = uuid("id")
    val name: Column<String> = text("name")
    val issuer: Column<String> = text("issuer")
    val issuedDate = date("issued_date")
    val expiresDate = date("expires_date").nullable()
    val credentialUrl: Column<String?> = text("credential_url").nullable()
    val sortOrder: Column<Int> = integer("sort_order")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object SkillsTable : Table("portfolio.skills") {
    val id: Column<Uuid> = uuid("id")
    val name: Column<String> = varchar("name", SHORT_TEXT_MAX_LENGTH).uniqueIndex()
    val category: Column<String> = text("category")
    val isFeatured: Column<Boolean> = bool("is_featured")
    val sortOrder: Column<Int> = integer("sort_order")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object ExperienceSkillsTable : Table("portfolio.experience_skills") {
    val experienceId: Column<Uuid> = uuid("experience_id").references(ExperiencesTable.id)
    val skillId: Column<Uuid> = uuid("skill_id").references(SkillsTable.id)

    override val primaryKey: PrimaryKey = PrimaryKey(experienceId, skillId)
}

object ProjectsTable : Table("portfolio.projects") {
    val id: Column<Uuid> = uuid("id")
    val slug: Column<String> = varchar("slug", SHORT_TEXT_MAX_LENGTH).uniqueIndex()
    val title: Column<String> = text("title")
    val summary: Column<String> = text("summary")
    val caseStudyMarkdown: Column<String> = text("case_study_markdown")
    val repoUrl: Column<String?> = text("repo_url").nullable()
    val liveUrl: Column<String?> = text("live_url").nullable()
    val coverDocumentId: Column<Uuid?> = uuid("cover_document_id")
        .references(DocumentsTable.id)
        .nullable()
    val status: Column<ProjectStatus> = enumerationByName("status", STATUS_MAX_LENGTH, ProjectStatus::class)
    val isFeatured: Column<Boolean> = bool("is_featured")
    val sortOrder: Column<Int> = integer("sort_order")
    val createdAt: Column<OffsetDateTime> = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt: Column<OffsetDateTime> = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

object ProjectSkillsTable : Table("portfolio.project_skills") {
    val projectId: Column<Uuid> = uuid("project_id").references(ProjectsTable.id)
    val skillId: Column<Uuid> = uuid("skill_id").references(SkillsTable.id)

    override val primaryKey: PrimaryKey = PrimaryKey(projectId, skillId)
}

object WriteupsTable : Table("portfolio.writeups") {
    val id: Column<Uuid> = uuid("id")
    val slug: Column<String> = varchar("slug", SHORT_TEXT_MAX_LENGTH).uniqueIndex()
    val title: Column<String> = text("title")
    val subtitle: Column<String?> = text("subtitle").nullable()
    val summary: Column<String> = text("summary")
    val bodyMarkdown: Column<String> = text("body_markdown")
    val tags: Column<List<String>> = array<String>("tags")
    val status: Column<WriteupStatus> = enumerationByName("status", STATUS_MAX_LENGTH, WriteupStatus::class)
    val readingTimeMin: Column<Int> = integer("reading_time_min")
    val coverDocumentId: Column<Uuid?> = uuid("cover_document_id")
        .references(DocumentsTable.id)
        .nullable()
    val publishedAt: Column<OffsetDateTime?> = timestampWithTimeZone("published_at").nullable()
    val createdAt: Column<OffsetDateTime> = timestampWithTimeZone("created_at")
        .defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt: Column<OffsetDateTime> = timestampWithTimeZone("updated_at")
        .defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

/**
 * Parameterized PostgreSQL full-text search predicate against `portfolio.writeups.search_vector`.
 */
class WriteupFullTextSearchOp(private val queryText: String) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("portfolio.writeups.search_vector @@ plainto_tsquery('english', ")
        queryBuilder.registerArgument(TextColumnType(), queryText)
        queryBuilder.append(")")
    }
}

/**
 * Parameterized PostgreSQL `TEXT[]` containment predicate (`@>`).
 */
class TextArrayContainsOp(
    private val column: Column<List<String>>,
    private val value: String,
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(column)
        queryBuilder.append(" @> ARRAY[")
        queryBuilder.registerArgument(TextColumnType(), value)
        queryBuilder.append("]::text[]")
    }
}
