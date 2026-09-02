import Foundation
import UIKit

struct RelaySettings: Codable, Equatable {
    var homeAssistantURL: String
    var relayToken: String
    var relayID: String
    var ringID: String
    var ringName: String
    var peripheralID: UUID?
    var syncIntervalMinutes: Int

    init(
        homeAssistantURL: String = Bundle.main.fitorbDefault("FitorbDefaultHomeAssistantURL"),
        relayToken: String = Bundle.main.fitorbDefault("FitorbDefaultRelayToken"),
        relayID: String = Bundle.main.fitorbDefault("FitorbDefaultRelayID"),
        ringID: String = Bundle.main.fitorbDefault("FitorbDefaultRingID"),
        ringName: String = "",
        peripheralID: UUID? = nil,
        syncIntervalMinutes: Int = 10
    ) {
        self.homeAssistantURL = homeAssistantURL
        self.relayToken = relayToken
        self.relayID = relayID.isEmpty ? Self.deviceRelayID : relayID
        self.ringID = ringID
        self.ringName = ringName
        self.peripheralID = peripheralID
        self.syncIntervalMinutes = syncIntervalMinutes
    }

    private static var deviceRelayID: String {
        "ios-\(UIDevice.current.name.lowercased().replacingOccurrences(of: " ", with: "-"))"
    }

    var isConfigured: Bool {
        configurationIssues.isEmpty
    }

    var configurationIssues: [String] {
        var issues: [String] = []
        let address = homeAssistantURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let token = relayToken.trimmingCharacters(in: .whitespacesAndNewlines)
        let relay = relayID.trimmingCharacters(in: .whitespacesAndNewlines)
        let ring = ringID.trimmingCharacters(in: .whitespacesAndNewlines)
        if peripheralID == nil { issues.append("Select the scanned ring") }
        if ring.isEmpty { issues.append("Enter the Home Assistant ring ID (usually its Bluetooth MAC address)") }
        if let url = URL(string: address), let scheme = url.scheme?.lowercased(),
           ["http", "https"].contains(scheme), url.host != nil {
            // Valid Home Assistant address.
        } else {
            issues.append("Enter a complete Home Assistant URL, including http:// or https://")
        }
        if !token.hasPrefix("fitorb_relay_") { issues.append("Paste a Fitorb relay token") }
        if relay.isEmpty { issues.append("Enter a relay ID for this iPhone") }
        return issues
    }

    mutating func normalize() {
        homeAssistantURL = homeAssistantURL.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        relayToken = relayToken.trimmingCharacters(in: .whitespacesAndNewlines)
        relayID = relayID.trimmingCharacters(in: .whitespacesAndNewlines)
        ringID = ringID.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        ringName = ringName.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private extension Bundle {
    func fitorbDefault(_ key: String) -> String {
        let value = object(forInfoDictionaryKey: key) as? String ?? ""
        return value.contains("$(") ? "" : value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

final class SettingsStore {
    private let defaults = UserDefaults.standard
    private let settingsKey = "relay.settings.v1"
    private let receiptKey = "relay.lastReceipt.v1"

    func load() -> RelaySettings {
        guard let data = defaults.data(forKey: settingsKey),
              let value = try? JSONDecoder().decode(RelaySettings.self, from: data) else { return RelaySettings() }
        return value
    }

    func save(_ settings: RelaySettings) {
        defaults.set(try? JSONEncoder().encode(settings), forKey: settingsKey)
    }

    func loadReceipt() -> SendReceipt? {
        defaults.data(forKey: receiptKey).flatMap { try? JSONDecoder().decode(SendReceipt.self, from: $0) }
    }

    func saveReceipt(_ receipt: SendReceipt) {
        defaults.set(try? JSONEncoder().encode(receipt), forKey: receiptKey)
    }
}

actor SampleQueue {
    private var samples: [RelaySample] = []
    private let url: URL

    init() {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        url = root.appendingPathComponent("fitorb-relay-queue.json")
        if let data = try? Data(contentsOf: url), let saved = try? JSONDecoder().decode([RelaySample].self, from: data) { samples = saved }
    }

    func append(_ newSamples: [RelaySample]) {
        let existing = Set(samples.map(\.sampleID))
        samples.append(contentsOf: newSamples.filter { !existing.contains($0.sampleID) })
        persist()
    }

    func pending() -> [RelaySample] { samples }
    func remove(ids: Set<String>) { samples.removeAll { ids.contains($0.sampleID) }; persist() }
    private func persist() { try? JSONEncoder().encode(samples).write(to: url, options: .atomic) }
}
