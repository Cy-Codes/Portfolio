package codes.cy.portfolio.server.db

import codes.cy.portfolio.core.error.ApiError
import codes.cy.portfolio.core.error.FieldViolation
import codes.cy.portfolio.server.db.errors.ConflictException
import codes.cy.portfolio.server.db.errors.ForbiddenException
import codes.cy.portfolio.server.db.errors.NotFoundException
import codes.cy.portfolio.server.db.errors.UnauthorizedException
import codes.cy.portfolio.server.db.errors.ValidationException
import codes.cy.portfolio.server.db.errors.toApiError
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ApiErrorMappingTest {
    @Test
    fun `maps domain exceptions to corresponding ApiError variants`() {
        val validation = ValidationException(
            message = "Bad payload",
            fieldErrors = listOf(FieldViolation("slug", "Invalid format")),
        ).toApiError(isDevelopment = false)
        assertIs<ApiError.ValidationError>(validation)
        assertEquals(1, validation.fieldErrors.size)

        assertIs<ApiError.Unauthorized>(UnauthorizedException("No token").toApiError(isDevelopment = false))
        assertIs<ApiError.Forbidden>(ForbiddenException("Wrong role").toApiError(isDevelopment = false))

        val notFound = NotFoundException(resource = "writeup", identifier = "missing").toApiError(false)
        assertIs<ApiError.NotFound>(notFound)
        assertEquals("writeup", notFound.resource)
        assertEquals("missing", notFound.identifier)

        assertIs<ApiError.Conflict>(ConflictException("Duplicate", "uk_slug").toApiError(false))
    }

    @Test
    fun `maps causal PostgreSQL SQLState integrity codes`() {
        val uniqueWrapper = RuntimeException(
            "Exposed wrapper",
            SQLException("duplicate key value", "23505"),
        )
        assertIs<ApiError.Conflict>(uniqueWrapper.toApiError(isDevelopment = false))

        val fkWrapper = RuntimeException(
            "Exposed wrapper",
            SQLException("violates foreign key constraint", "23503"),
        )
        assertIs<ApiError.Conflict>(fkWrapper.toApiError(isDevelopment = false))

        val checkWrapper = SQLException("violates check constraint", "23514")
        assertIs<ApiError.ValidationError>(checkWrapper.toApiError(isDevelopment = false))

        val malformedUuidWrapper = SQLException("invalid input syntax for type uuid", "22P02")
        assertIs<ApiError.ValidationError>(malformedUuidWrapper.toApiError(isDevelopment = false))
    }

    @Test
    fun `sanitizes internal errors outside development environments`() {
        val secretLeakingException = IllegalStateException("jdbc:postgresql://secret-host:5432/portfolio failed")

        val prodError = secretLeakingException.toApiError(isDevelopment = false, traceId = "trace-123")
        assertIs<ApiError.InternalError>(prodError)
        assertEquals(ApiError.GENERIC_INTERNAL_MESSAGE, prodError.message)
        assertEquals("trace-123", prodError.traceId)
        assertFalse(prodError.message.contains("secret-host"))

        val devError = secretLeakingException.toApiError(isDevelopment = true, traceId = "trace-123")
        assertIs<ApiError.InternalError>(devError)
        assertEquals("jdbc:postgresql://secret-host:5432/portfolio failed", devError.message)
    }
}
