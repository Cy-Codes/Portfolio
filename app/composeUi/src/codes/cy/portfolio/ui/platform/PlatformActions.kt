package codes.cy.portfolio.ui.platform

import codes.cy.portfolio.core.model.DocumentCategory
import codes.cy.portfolio.core.model.ProjectStatus
import codes.cy.portfolio.core.model.WriteupStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

interface PlatformActions {
    fun openExternalUrl(url: String)
    fun triggerDownload(url: String, filename: String)
}

object NoOpPlatformActions : PlatformActions {
    override fun openExternalUrl(url: String) = Unit
    override fun triggerDownload(url: String, filename: String) = Unit
}

private const val BYTES_PER_KIB: Long = 1024L
private const val BYTES_PER_MIB: Long = BYTES_PER_KIB * BYTES_PER_KIB
private const val TENTHS_SCALE: Long = 10L
private const val MONTH_COUNT: Int = 12

private val SHORT_MONTH_NAMES: List<String> = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun Long.formatByteSize(): String =
    when {
        this < 0L -> "0 B"
        this < BYTES_PER_KIB -> "$this B"
        this < BYTES_PER_MIB -> {
            val wholeKib = this / BYTES_PER_KIB
            val tenths = ((this % BYTES_PER_KIB) * TENTHS_SCALE) / BYTES_PER_KIB
            if (tenths == 0L) "$wholeKib KB" else "$wholeKib.$tenths KB"
        }
        else -> {
            val wholeMib = this / BYTES_PER_MIB
            val tenths = ((this % BYTES_PER_MIB) * TENTHS_SCALE) / BYTES_PER_MIB
            "$wholeMib.$tenths MB"
        }
    }

fun LocalDate.formatMonthYear(): String {
    val monthIdx = monthNumber - 1
    val monthLabel = if (monthIdx in 0 until MONTH_COUNT) SHORT_MONTH_NAMES[monthIdx] else monthNumber.toString()
    return "$monthLabel $year"
}

fun Instant.formatShortDate(): String {
    val isoDatePart = toString().substringBefore('T')
    return runCatching { LocalDate.parse(isoDatePart).formatMonthYear() }.getOrDefault(isoDatePart)
}

fun ProjectStatus.displayLabel(): String =
    when (this) {
        ProjectStatus.ACTIVE -> "Active"
        ProjectStatus.ARCHIVED -> "Archived"
        ProjectStatus.PROTOTYPE -> "Prototype"
    }

fun WriteupStatus.displayLabel(): String =
    when (this) {
        WriteupStatus.DRAFT -> "Draft"
        WriteupStatus.PUBLISHED -> "Published"
        WriteupStatus.ARCHIVED -> "Archived"
    }

fun DocumentCategory.displayLabel(): String =
    when (this) {
        DocumentCategory.RESUME -> "Resume"
        DocumentCategory.PAPER -> "Paper"
        DocumentCategory.SLIDES -> "Slides"
        DocumentCategory.IMAGE -> "Image"
        DocumentCategory.ATTACHMENT -> "Attachment"
    }
