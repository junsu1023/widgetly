//
//  SendSignalHttpClientTests.swift
//  DdayWidgetExtensionTests
//

import Testing
import Foundation

private final class FakeSendSignalURLSession: SendSignalURLSession {
    private let responseBody: String

    init(returning responseBody: String) {
        self.responseBody = responseBody
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        let data = responseBody.data(using: .utf8)!
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
        return (data, response)
    }
}

private func fakeSession(returning responseBody: String) -> SendSignalURLSession {
    FakeSendSignalURLSession(returning: responseBody)
}

struct SendSignalHttpClientTests {
    @Test func testRateLimitedResponseIsParsedWithoutThrowing() async throws {
        let client = SendSignalHttpClient(session: fakeSession(returning: #"{"result":{"rateLimited":true}}"#))
        let result = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s1", authToken: "tok")
        #expect(result.rateLimited)
    }

    @Test func testSuccessfulNonRateLimitedResponseIsParsed() async throws {
        let client = SendSignalHttpClient(session: fakeSession(returning: #"{"result":{"rateLimited":false}}"#))
        let result = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s2", authToken: "tok")
        #expect(!result.rateLimited)
    }

    @Test func testServerErrorEnvelopeThrowsInsteadOfSucceeding() async throws {
        let client = SendSignalHttpClient(session: fakeSession(returning: #"{"error":{"status":"FAILED_PRECONDITION","message":"Connection is not active for this user"}}"#))
        await #expect(throws: SendSignalServerError.self) {
            _ = try await client.sendSignal(connectionId: "c1", preset: "heart", signalId: "s3", authToken: "tok")
        }
    }
}
