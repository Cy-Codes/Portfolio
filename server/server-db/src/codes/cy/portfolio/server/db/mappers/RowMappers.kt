package codes.cy.portfolio.server.db.mappers

import codes.cy.portfolio.core.model.CertificationDto
import codes.cy.portfolio.core.model.DocumentDto
import codes.cy.portfolio.core.model.EducationDto
import codes.cy.portfolio.core.model.ExperienceDto
import codes.cy.portfolio.core.model.ProfileDto
import codes.cy.portfolio.core.model.ProjectDetailDto
import codes.cy.portfolio.core.model.ProjectSummaryDto
import codes.cy.portfolio.core.model.SkillDto
import codes.cy.portfolio.core.model.WriteupDetailDto
import codes.cy.portfolio.core.model.WriteupSummaryDto
import codes.cy.portfolio.server.db.tables.CertificationsTable
import codes.cy.portfolio.server.db.tables.DocumentsTable
import codes.cy.portfolio.server.db.tables.EducationTable
import codes.cy.portfolio.server.db.tables.ExperiencesTable
import codes.cy.portfolio.server.db.tables.ProfileTable
import codes.cy.portfolio.server.db.tables.ProjectsTable
import codes.cy.portfolio.server.db.tables.SkillsTable
import codes.cy.portfolio.server.db.tables.WriteupsTable
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.ResultRow

fun OffsetDateTime.toKotlinInstant(): Instant =
    Instant.fromEpochSeconds(toEpochSecond(), nano.toLong())

fun Instant.toUtcOffsetDateTime(): OffsetDateTime =
    java.time.Instant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong()).atOffset(ZoneOffset.UTC)

fun java.time.LocalDate.toKmpLocalDate(): kotlinx.datetime.LocalDate = toKotlinLocalDate()

fun kotlinx.datetime.LocalDate.toJvmLocalDate(): java.time.LocalDate = toJavaLocalDate()

fun ResultRow.toSkillDto(): SkillDto =
    SkillDto(
        id = this[SkillsTable.id].toString(),
        name = this[SkillsTable.name],
        category = this[SkillsTable.category],
        isFeatured = this[SkillsTable.isFeatured],
        sortOrder = this[SkillsTable.sortOrder],
    )

fun ResultRow.toProfileDto(): ProfileDto =
    ProfileDto(
        fullName = this[ProfileTable.fullName],
        headline = this[ProfileTable.headline],
        bioMarkdown = this[ProfileTable.bioMarkdown],
        location = this[ProfileTable.location],
        availability = this[ProfileTable.availability],
        socialLinks = this[ProfileTable.socialLinks],
        resumeDocumentId = this[ProfileTable.resumeDocumentId]?.toString(),
        updatedAt = this[ProfileTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toExperienceDto(skills: List<SkillDto>): ExperienceDto =
    ExperienceDto(
        id = this[ExperiencesTable.id].toString(),
        company = this[ExperiencesTable.company],
        title = this[ExperiencesTable.title],
        location = this[ExperiencesTable.location],
        startDate = this[ExperiencesTable.startDate].toKmpLocalDate(),
        endDate = this[ExperiencesTable.endDate]?.toKmpLocalDate(),
        isCurrent = this[ExperiencesTable.isCurrent],
        highlights = this[ExperiencesTable.highlights],
        skills = skills,
        sortOrder = this[ExperiencesTable.sortOrder],
        createdAt = this[ExperiencesTable.createdAt].toKotlinInstant(),
        updatedAt = this[ExperiencesTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toEducationDto(): EducationDto =
    EducationDto(
        id = this[EducationTable.id].toString(),
        institution = this[EducationTable.institution],
        degree = this[EducationTable.degree],
        fieldOfStudy = this[EducationTable.fieldOfStudy],
        startDate = this[EducationTable.startDate].toKmpLocalDate(),
        endDate = this[EducationTable.endDate]?.toKmpLocalDate(),
        sortOrder = this[EducationTable.sortOrder],
    )

fun ResultRow.toCertificationDto(): CertificationDto =
    CertificationDto(
        id = this[CertificationsTable.id].toString(),
        name = this[CertificationsTable.name],
        issuer = this[CertificationsTable.issuer],
        issuedDate = this[CertificationsTable.issuedDate].toKmpLocalDate(),
        expiresDate = this[CertificationsTable.expiresDate]?.toKmpLocalDate(),
        credentialUrl = this[CertificationsTable.credentialUrl],
        sortOrder = this[CertificationsTable.sortOrder],
    )

fun ResultRow.toProjectSummaryDto(skills: List<SkillDto>): ProjectSummaryDto =
    ProjectSummaryDto(
        id = this[ProjectsTable.id].toString(),
        slug = this[ProjectsTable.slug],
        title = this[ProjectsTable.title],
        summary = this[ProjectsTable.summary],
        repoUrl = this[ProjectsTable.repoUrl],
        liveUrl = this[ProjectsTable.liveUrl],
        coverDocumentId = this[ProjectsTable.coverDocumentId]?.toString(),
        status = this[ProjectsTable.status],
        isFeatured = this[ProjectsTable.isFeatured],
        skills = skills,
        sortOrder = this[ProjectsTable.sortOrder],
        updatedAt = this[ProjectsTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toProjectDetailDto(skills: List<SkillDto>): ProjectDetailDto =
    ProjectDetailDto(
        id = this[ProjectsTable.id].toString(),
        slug = this[ProjectsTable.slug],
        title = this[ProjectsTable.title],
        summary = this[ProjectsTable.summary],
        caseStudyMarkdown = this[ProjectsTable.caseStudyMarkdown],
        repoUrl = this[ProjectsTable.repoUrl],
        liveUrl = this[ProjectsTable.liveUrl],
        coverDocumentId = this[ProjectsTable.coverDocumentId]?.toString(),
        status = this[ProjectsTable.status],
        isFeatured = this[ProjectsTable.isFeatured],
        skills = skills,
        sortOrder = this[ProjectsTable.sortOrder],
        createdAt = this[ProjectsTable.createdAt].toKotlinInstant(),
        updatedAt = this[ProjectsTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toWriteupSummaryDto(): WriteupSummaryDto =
    WriteupSummaryDto(
        id = this[WriteupsTable.id].toString(),
        slug = this[WriteupsTable.slug],
        title = this[WriteupsTable.title],
        subtitle = this[WriteupsTable.subtitle],
        summary = this[WriteupsTable.summary],
        tags = this[WriteupsTable.tags],
        status = this[WriteupsTable.status],
        readingTimeMin = this[WriteupsTable.readingTimeMin],
        coverDocumentId = this[WriteupsTable.coverDocumentId]?.toString(),
        publishedAt = this[WriteupsTable.publishedAt]?.toKotlinInstant(),
        updatedAt = this[WriteupsTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toWriteupDetailDto(): WriteupDetailDto =
    WriteupDetailDto(
        id = this[WriteupsTable.id].toString(),
        slug = this[WriteupsTable.slug],
        title = this[WriteupsTable.title],
        subtitle = this[WriteupsTable.subtitle],
        summary = this[WriteupsTable.summary],
        bodyMarkdown = this[WriteupsTable.bodyMarkdown],
        tags = this[WriteupsTable.tags],
        status = this[WriteupsTable.status],
        readingTimeMin = this[WriteupsTable.readingTimeMin],
        coverDocumentId = this[WriteupsTable.coverDocumentId]?.toString(),
        publishedAt = this[WriteupsTable.publishedAt]?.toKotlinInstant(),
        createdAt = this[WriteupsTable.createdAt].toKotlinInstant(),
        updatedAt = this[WriteupsTable.updatedAt].toKotlinInstant(),
    )

fun ResultRow.toDocumentDto(downloadCount: Long = 0L): DocumentDto =
    DocumentDto(
        id = this[DocumentsTable.id].toString(),
        title = this[DocumentsTable.title],
        category = this[DocumentsTable.category],
        originalFilename = this[DocumentsTable.originalFilename],
        mimeType = this[DocumentsTable.mimeType],
        sizeBytes = this[DocumentsTable.sizeBytes],
        sha256 = this[DocumentsTable.sha256],
        storageKey = this[DocumentsTable.storageKey],
        isPublic = this[DocumentsTable.isPublic],
        downloadCount = downloadCount,
        createdAt = this[DocumentsTable.createdAt].toKotlinInstant(),
    )
