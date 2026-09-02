import Foundation

enum RelayValue: Codable, Equatable {
    case int(Int), double(Double), string(String), bool(Bool)

    init(from decoder: Decoder) throws {
        let box = try decoder.singleValueContainer()
        if let value = try? box.decode(Bool.self) { self = .bool(value) }
        else if let value = try? box.decode(Int.self) { self = .int(value) }
        else if let value = try? box.decode(Double.self) { self = .double(value) }
        else { self = .string(try box.decode(String.self)) }
    }

    func encode(to encoder: Encoder) throws {
        var box = encoder.singleValueContainer()
        switch self {
        case .int(let value): try box.encode(value)
        case .double(let value): try box.encode(value)
        case .string(let value): try box.encode(value)
        case .bool(let value): try box.encode(value)
        }
    }

    var display: String {
        switch self {
        case .int(let value): return String(value)
        case .double(let value): return String(format: "%.1f", value)
        case .string(let value): return value
        case .bool(let value): return value ? "Yes" : "No"
        }
    }
}
struct RelaySample: Codable, Identifiable, Equatable {
    let sampleID: String
    let ringID: String
    let metric: String
    let timestamp: String
    let value: RelayValue
    let unit: String?
    var source = "ios_relay"
    let capturedAt: String
    let localDate: String?
    let rawHex: String?
    var protocolVersion = 1

    var id: String { sampleID }

    enum CodingKeys: String, CodingKey {
        case sampleID = "sample_id", ringID = "ring_id", metric, timestamp, value, unit
        case source, capturedAt = "captured_at", localDate = "local_date"
        case rawHex = "raw_hex", protocolVersion = "protocol_version"
    }
}

struct RelayBatch: Encodable {
    let relayID: String
    let ringID: String
    let appVersion: String
    let protocolVersion = 1
    let sentAt: String
    let samples: [RelaySample]
    let backlog: Int

    enum CodingKeys: String, CodingKey {
        case relayID = "relay_id", ringID = "ring_id", appVersion = "app_version"
        case protocolVersion = "protocol_version", sentAt = "sent_at", samples, backlog
    }
}

struct RelayAck: Decodable {
    struct Rejected: Decodable { let sampleID: String; let reason: String
        enum CodingKeys: String, CodingKey { case sampleID = "sample_id", reason }
    }
    let accepted: [String]
    let duplicates: [String]
    let rejected: [Rejected]
}

struct SendReceipt: Codable, Equatable {
    let date: Date
    let sent: Int
    let accepted: Int
    let duplicates: Int
    let rejected: Int
}

struct RingChoice: Identifiable, Equatable {
    let id: UUID
    let name: String
    let rssi: Int
}
