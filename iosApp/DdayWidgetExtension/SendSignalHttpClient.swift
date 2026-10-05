//
//  SendSignalHttpClient.swift
//  DdayWidgetExtension
//

import Foundation

struct SendSignalResult: Decodable {
    let rateLimited: Bool
}

/// An `HttpsError` the `sendSignal` callable reported (e.g. rate limit logic
/// aside, a genuine failure like `failed-precondition`/`invalid-argument`).
struct SendSignalServerError: Error {
    let message: String
    let status: String?
}

/// The response body matched neither the callable success nor error envelope
/// shape — something is wrong with the endpoint/wire format, not the request.
struct SendSignalInvalidResponse: Error {}

/// Narrow seam over `URLSession.data(for:)` so tests can substitute a fake
/// response without touching the network. `URLSession` itself already
/// satisfies this (see its `data(for:)` method), so production code needs no
/// adapter.
protocol SendSignalURLSession {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: SendSignalURLSession {}

/// Calls the `sendSignal` Cloud Functions callable directly over HTTPS using
/// Firebase's documented callable wire protocol — POST `{"data": {...}}`,
/// response is `{"result": {...}}` on success or `{"error": {message,
/// status}}` on failure — instead of the Firebase SDK. This target
/// deliberately carries no Firebase SDK dependency (memory-constrained
/// widget/intent extension; see docs/widget-development.md).
final class SendSignalHttpClient {
    private let session: SendSignalURLSession
    private let endpoint: URL

    // ponytail: project id + region are hardcoded rather than read from
    // config, since this target intentionally has no GoogleService-Info.plist
    // equivalent. Functions v2 defaults to us-central1 (no region() override
    // anywhere in functions/src/*.ts) and the project id is fixed
    // (widgetly-53e09, per .firebaserc) — update both together if the
    // backend's deploy target ever changes.
    init(
        session: SendSignalURLSession = URLSession.shared,
        endpoint: URL = URL(string: "https://us-central1-widgetly-53e09.cloudfunctions.net/sendSignal")!
    ) {
        self.session = session
        self.endpoint = endpoint
    }

    func sendSignal(connectionId: String, preset: String, signalId: String, authToken: String) async throws -> SendSignalResult {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "data": [
                "connectionId": connectionId,
                "preset": preset,
                "signalId": signalId,
            ],
        ])

        let (data, _) = try await session.data(for: request)

        if let envelope = try? JSONDecoder().decode(SendSignalSuccessEnvelope.self, from: data) {
            return envelope.result
        }
        if let errorEnvelope = try? JSONDecoder().decode(SendSignalErrorEnvelope.self, from: data) {
            throw SendSignalServerError(message: errorEnvelope.error.message, status: errorEnvelope.error.status)
        }
        throw SendSignalInvalidResponse()
    }
}

private struct SendSignalSuccessEnvelope: Decodable {
    let result: SendSignalResult
}

private struct SendSignalErrorEnvelope: Decodable {
    struct Body: Decodable {
        let message: String
        let status: String?
    }
    let error: Body
}
