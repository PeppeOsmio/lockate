package com.peppeosmio.lockate.utils

import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException

class ErrorHandlerTest {

    private suspend fun mockResponse(
        body: String, status: HttpStatusCode, contentType: String = "application/json"
    ): HttpResponse {
        val mockEngine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, contentType)
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
        return client.get("http://test")
    }

    // handleUnauthorized

    @Test
    fun `handleUnauthorized with invalid_api_key error throws InvalidApiKeyException`() = runTest {
        val response = mockResponse("""{"error":"invalid_api_key"}""", HttpStatusCode.Unauthorized)

        try {
            ErrorHandler.handleUnauthorized(response)
            fail("Expected InvalidApiKeyException")
        } catch (e: InvalidApiKeyException) {
            // expected
        }
    }

    @Test
    fun `handleUnauthorized with missing_api_key error throws InvalidApiKeyException`() = runTest {
        val response = mockResponse("""{"error":"missing_api_key"}""", HttpStatusCode.Unauthorized)

        try {
            ErrorHandler.handleUnauthorized(response)
            fail("Expected InvalidApiKeyException")
        } catch (e: InvalidApiKeyException) {
            // expected
        }
    }

    @Test
    fun `handleUnauthorized with another error string throws UnauthorizedException`() = runTest {
        val response = mockResponse("""{"error":"something_else"}""", HttpStatusCode.Unauthorized)

        try {
            ErrorHandler.handleUnauthorized(response)
            fail("Expected UnauthorizedException")
        } catch (e: UnauthorizedException) {
            // expected
        }
    }

    @Test
    fun `handleUnauthorized with a malformed body throws JsonConvertException, not UnauthorizedException`() {
        // NB: response.body<T>() wraps a malformed body in Ktor's JsonConvertException, which
        // does NOT extend kotlinx.serialization.SerializationException - so handleUnauthorized's
        // `catch (e: SerializationException)` never catches it and it propagates uncaught. This
        // documents current behavior; it is likely an unintended gap, not something this test
        // suite fixes.
        assertThrows(JsonConvertException::class.java) {
            runTest {
                val response = mockResponse("not json", HttpStatusCode.Unauthorized)
                ErrorHandler.handleUnauthorized(response)
            }
        }
    }

    // handleGeneric

    @Test
    fun `handleGeneric with a valid JSON body throws APIException with status and body`() =
        runTest {
            val response = mockResponse("""{"error":"boom"}""", HttpStatusCode.InternalServerError)

            try {
                ErrorHandler.handleGeneric(response)
                fail("Expected APIException")
            } catch (e: APIException) {
                assertEquals(500, e.statusCode)
                assertEquals("boom", e.body)
            }
        }

    @Test
    fun `handleGeneric with a malformed body throws JsonConvertException, not APIException`() {
        // Same gap as handleUnauthorized above: JsonConvertException isn't a SerializationException.
        assertThrows(JsonConvertException::class.java) {
            runTest {
                val response = mockResponse("not json", HttpStatusCode.InternalServerError)
                ErrorHandler.handleGeneric(response)
            }
        }
    }

    // runAndHandleException

    @Test
    fun `runAndHandleException on success returns the value with no errorInfo`() = runTest {
        val result = ErrorHandler.runAndHandleException { "ok" }

        assertEquals("ok", result.value)
        assertEquals(null, result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps RemoteAGNotFoundException to the matching ErrorInfo`() =
        runTest {
            val exception = RemoteAGNotFoundException()

            val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

            assertEquals(ErrorInfo.remoteAGNotFoundException(exception), result.errorInfo)
        }

    @Test
    fun `runAndHandleException maps LocalAGNotFoundException to the matching ErrorInfo`() =
        runTest {
            val exception = LocalAGNotFoundException()

            val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

            assertEquals(ErrorInfo.localAGNotFoundException(exception), result.errorInfo)
        }

    @Test
    fun `runAndHandleException maps ConnectException to the connect ErrorInfo`() = runTest {
        val exception = ConnectException("boom")

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.connectException(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps NoRouteToHostException to the connect ErrorInfo`() = runTest {
        val exception = NoRouteToHostException("boom")

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.connectException(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps SerializationException to the matching ErrorInfo`() = runTest {
        val exception = SerializationException("boom")

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.serializationException(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps InvalidApiKeyException to the matching ErrorInfo`() = runTest {
        val exception = InvalidApiKeyException()

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.invalidApiKeyException(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps AGMemberUnauthorizedException to the matching ErrorInfo`() =
        runTest {
            val exception = AGMemberUnauthorizedException()

            val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

            assertEquals(ErrorInfo.notMemberOfAGException(exception), result.errorInfo)
        }

    @Test
    fun `runAndHandleException maps APIException to the matching ErrorInfo`() = runTest {
        val exception = APIException(statusCode = 500, body = "boom")

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.apiException(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException maps a generic exception to the generic ErrorInfo`() = runTest {
        val exception = IllegalStateException("boom")

        val result = ErrorHandler.runAndHandleException<Unit> { throw exception }

        assertEquals(ErrorInfo.exception(exception), result.errorInfo)
    }

    @Test
    fun `runAndHandleException lets a non-null customHandler short-circuit the standard mapping`() =
        runTest {
            val exception = IllegalStateException("boom")
            val customErrorInfo =
                ErrorInfo(title = "custom", body = "custom body", exception = exception)

            val result = ErrorHandler.runAndHandleException<Unit>(
                customHandler = { customErrorInfo }) { throw exception }

            assertEquals(customErrorInfo, result.errorInfo)
        }

    @Test
    fun `runAndHandleException with a customHandler returning null yields a null errorInfo`() =
        runTest {
            // NB: despite the KDoc's description ("re-throws e to let it propagate and be handled
            // by the other default handlers"), the implementation always returns immediately with
            // customHandler(e) as errorInfo when customHandler is non-null - it does not fall
            // through to the standard `when` mapping if customHandler returns null. A customHandler
            // must rethrow internally (not return null) to defer to the standard mapping. This
            // documents current behavior, not something this test suite fixes.
            val exception = RemoteAGNotFoundException()

            val result = ErrorHandler.runAndHandleException<Unit>(
                customHandler = { null }) { throw exception }

            assertNull(result.errorInfo)
        }

    @Test
    fun `runAndHandleException customHandler that rethrows defers to the standard mapping`() =
        runTest {
            val exception = RemoteAGNotFoundException()

            val result = ErrorHandler.runAndHandleException<Unit>(
                customHandler = { throw it }) { throw exception }

            assertEquals(ErrorInfo.remoteAGNotFoundException(exception), result.errorInfo)
        }
}
