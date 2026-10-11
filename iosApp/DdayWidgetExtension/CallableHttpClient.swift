//
//  CallableHttpClient.swift
//  DdayWidgetExtension
//

import Foundation

struct SendSignalResult: Decodable {
    let rateLimited: Bool
}

/// `functions/src/getConnection.ts`'s return shape. `sentAt` is an ISO-8601
/// string, not a Firestore `Timestamp` - that callable flattens it on purpose,
/// because this client decodes the raw callable JSON itself and has no Firebase
/// SDK to unwrap a Timestamp with.
struct GetConnectionResult: Decodable {
    struct Signal: Decodable {
        let presetType: String
        let sentAt: String
    }

    let status: String
    let streak: Int
    let memberUids: [String]
    let lastSignalByUid: [String: Signal]
}

/// An `HttpsError` a callable reported (e.g. `failed-precondition`,
/// `invalid-argument`, `not-found`).
struct CallableServerError: Error {
    let message: String
    let status: String?
}

/// The response body matched neither the callable success nor error envelope
/// shape — something is wrong with the endpoint/wire format, not the request.
struct CallableInvalidResponse: Error {}

/// Narrow seam over `URLSession.data(for:)` so tests can substitute a fake
/// response without touching the network. `URLSession` itself already
/// satisfies this (see its `data(for:)` method), so production code needs no
/// adapter.
protocol CallableURLSession {
    func data(for request: URLRequest) async throws -> (Data, URLResponse)
}

extension URLSession: CallableURLSession {}

/// Calls Cloud Functions callables directly over HTTPS using Firebase's
/// documented callable wire protocol — POST `{"data": {...}}`, response is
/// `{"result": {...}}` on success or `{"error": {message, status}}` on failure —
/// instead of the Firebase SDK. This target deliberately carries no Firebase SDK
/// dependency (memory-constrained widget/intent extension; see
/// docs/widget-development.md).
final class CallableHttpClient {
    private let session: CallableURLSession
    private let baseURL: URL

    // ponytail: project id + region are hardcoded rather than read from
    // config, since this target intentionally has no GoogleService-Info.plist
    // equivalent. Functions v2 defaults to us-central1 (no region() override
    // anywhere in functions/src/*.ts) and the project id is fixed
    // (widgetly-53e09, per .firebaserc) — update both together if the
    // backend's deploy target ever changes.
    init(
        session: CallableURLSession = URLSession.shared,
        baseURL: URL = URL(string: "https://us-central1-widgetly-53e09.cloudfunctions.net")!
    ) {
        self.session = session
        self.baseURL = baseURL
    }

    func sendSignal(connectionId: String, preset: String, signalId: String, authToken: String) async throws -> SendSignalResult {
        try await call(
            "sendSignal",
            data: ["connectionId": connectionId, "preset": preset, "signalId": signalId],
            authToken: authToken
        )
    }

    func getConnection(connectionId: String, authToken: String) async throws -> GetConnectionResult {
        try await call("getConnection", data: ["connectionId": connectionId], authToken: authToken)
    }

    /// The envelope handling every callable shares. Generic over the `result`
    /// payload so a new callable is one method above, not another copy of this.
    private func call<T: Decodable>(_ name: String, data: [String: String], authToken: String) async throws -> T {
        var request = URLRequest(url: baseURL.appendingPathComponent(name))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(authToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["data": data])

        let (body, _) = try await session.data(for: request)

        if let envelope = try? JSONDecoder().decode(CallableSuccessEnvelope<T>.self, from: body) {
            return envelope.result
        }
        if let errorEnvelope = try? JSONDecoder().decode(CallableErrorEnvelope.self, from: body) {
            throw CallableServerError(message: errorEnvelope.error.message, status: errorEnvelope.error.status)
        }
        throw CallableInvalidResponse()
    }
}

private struct CallableSuccessEnvelope<T: Decodable>: Decodable {
    let result: T
}

private struct CallableErrorEnvelope: Decodable {
    struct Body: Decodable {
        let message: String
        let status: String?
    }
    let error: Body
}
