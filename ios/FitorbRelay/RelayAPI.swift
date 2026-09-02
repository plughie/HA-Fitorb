import Foundation

enum RelayError: LocalizedError {
    case invalidURL, invalidResponse, http(Int), message(String)
    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid Home Assistant URL"
        case .invalidResponse: return "Invalid response from Home Assistant"
        case .http(let code): return "Home Assistant returned HTTP \(code)"
        case .message(let text): return text
        }
    }
}
struct RelayAPI {
    func upload(settings: RelaySettings, samples: [RelaySample]) async throws -> RelayAck {
        guard var url = URL(string: settings.homeAssistantURL) else { throw RelayError.invalidURL }
        url.append(path: "api/fitorb/relay/v1/samples")
        let batch = RelayBatch(relayID: settings.relayID, ringID: settings.ringID,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
            sentAt: ISO8601DateFormatter().string(from: Date()), samples: samples, backlog: samples.count)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(settings.relayToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(batch)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw RelayError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else { throw RelayError.http(http.statusCode) }
        return try JSONDecoder().decode(RelayAck.self, from: data)
    }
}
