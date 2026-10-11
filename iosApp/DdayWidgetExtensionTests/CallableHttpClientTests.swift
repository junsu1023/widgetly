//
//  CallableHttpClientTests.swift
//  DdayWidgetExtensionTests
//

import Testing
import Foundation

private final class FakeCallableURLSession: CallableURLSession {
    private let responseBody: String
    /// Captured so a test can assert which callable the client actually posted to.
    private(set) var lastRequest: URLRequest?

    init(returning responseBody: String) {
        self.responseBody = responseBody
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        lastRequest = request
        let data = responseBody.data(using: .utf8)!
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
        return (data, response)
    }
}

private func fakeSession(returning responseBody: String) -> CallableURLSession {
    FakeCallableURLSession(returning: responseBody)
}

struct CallableHttpClientTests {
    @Test func testRateLimitedResponseIsParsedWithoutThrowing() async throws {
        let client = CallableHttpClient(session: fakeSession(returning: #"{"result":{"rateLimited":true}}"#))
        let result = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s1", authToken: "tok")
        #expect(result.rateLimited)
    }

    @Test func testSuccessfulNonRateLimitedResponseIsParsed() async throws {
        let client = CallableHttpClient(session: fakeSession(returning: #"{"result":{"rateLimited":false}}"#))
        let result = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s2", authToken: "tok")
        #expect(!result.rateLimited)
    }

    @Test func testServerErrorEnvelopeThrowsInsteadOfSucceeding() async throws {
        let client = CallableHttpClient(session: fakeSession(returning: #"{"error":{"status":"FAILED_PRECONDITION","message":"Connection is not active for this user"}}"#))
        await #expect(throws: CallableServerError.self) {
            _ = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s3", authToken: "tok")
        }
    }

    @Test func testGetConnectionResponseIsParsed() async throws {
        let body = #"""
        {"result":{"status":"active","streak":7,"memberUids":["me","you"],
        "lastSignalByUid":{"you":{"presetType":"heart","sentAt":"2026-02-03T04:05:06.000Z"}}}}
        """#
        let client = CallableHttpClient(session: fakeSession(returning: body))

        let result = try await client.getConnection(connectionId: "c1", authToken: "tok")

        #expect(result.status == "active")
        #expect(result.streak == 7)
        #expect(result.memberUids == ["me", "you"])
        #expect(result.lastSignalByUid["you"]?.presetType == "heart")
    }

    @Test func testGetConnectionNotFoundThrowsServerError() async throws {
        let client = CallableHttpClient(session: fakeSession(returning: #"{"error":{"status":"NOT_FOUND","message":"Connection does not exist"}}"#))
        await #expect(throws: CallableServerError.self) {
            _ = try await client.getConnection(connectionId: "nope", authToken: "tok")
        }
    }

    @Test func testEachCallableGetsItsOwnEndpointPath() async throws {
        let session = FakeCallableURLSession(returning: #"{"result":{"rateLimited":false}}"#)
        let client = CallableHttpClient(session: session)

        _ = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s1", authToken: "tok")
        #expect(session.lastRequest?.url?.lastPathComponent == "sendSignal")
        #expect(session.lastRequest?.value(forHTTPHeaderField: "Authorization") == "Bearer tok")
    }
}
