package codes.cy.portfolio.core.error

import codes.cy.portfolio.core.serialization.PortfolioJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiErrorTest {
    @Test
    fun `maps each sealed variant to canonical HTTP status code`() {
        assertEquals(ApiError.HTTP_BAD_REQUEST, ApiError.ValidationError("Bad input").httpStatusCode())
        assertEquals(ApiError.HTTP_UNAUTHORIZED, ApiError.Unauthorized("Auth required").httpStatusCode())
        assertEquals(ApiError.HTTP_FORBIDDEN, ApiError.Forbidden("Denied").httpStatusCode())
        assertEquals(ApiError.HTTP_NOT_FOUND, ApiError.NotFound("Missing", "writeup", "abc").httpStatusCode())
        assertEquals(ApiError.HTTP_CONFLICT, ApiError.Conflict("Duplicate slug", "uq_slug").httpStatusCode())
        assertEquals(ApiError.HTTP_INTERNAL_SERVER_ERROR, ApiError.InternalError("Error", "trace-1").httpStatusCode())
    }

    @Test
    fun `serializes sealed hierarchy polymorphically with explicit type discriminator`() {
        val original: ApiError = ApiError.ValidationError(
            message = "Invalid query",
            fieldErrors = listOf(FieldViolation(field = "page", message = "Out of bounds")),
        )
        val json = PortfolioJson.encodeToString(ApiError.serializer(), original)
        assertTrue(json.contains("\"type\":\"validation_error\""))

        val decoded = PortfolioJson.decodeFromString(ApiError.serializer(), json)
        assertEquals(original, decoded)
    }
}
