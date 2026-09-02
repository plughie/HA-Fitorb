import Foundation

@MainActor
final class RelayViewModel: ObservableObject {
    @Published var settings: RelaySettings
    @Published var rings: [RingChoice] = []
    @Published var samples: [RelaySample] = []
    @Published var receipt: SendReceipt?
    @Published var status = "Ready"
    @Published var isSending = false
    @Published var showingSetup: Bool

    private let store = SettingsStore(), queue = SampleQueue(), collector = RingCollector(), api = RelayAPI()
    private var timerTask: Task<Void, Never>?, launched = false

    init() { let value = store.load(); settings = value; receipt = store.loadReceipt(); showingSetup = !value.isConfigured }

    func scan() async {
        status = "Scanning…"
        do {
            rings = try await collector.scan()
            if rings.count == 1, let ring = rings.first {
                choose(ring)
            } else {
                status = rings.isEmpty ? "No compatible ring found" : "Tap a ring below to select it"
            }
        } catch {
            status = error.localizedDescription
        }
    }
    func choose(_ ring: RingChoice) { settings.peripheralID = ring.id; settings.ringName = ring.name; status = "Selected \(ring.name)" }
    func save() {
        settings.normalize()
        settings.syncIntervalMinutes = min(60, max(1, settings.syncIntervalMinutes))
        store.save(settings)
        showingSetup = !settings.isConfigured
        scheduleTimer()
    }

    func startIfConfigured() async { guard !launched else { return }; launched = true; if settings.isConfigured { await send(); scheduleTimer() } }
    func becameActive() async { if settings.isConfigured && !launched { await startIfConfigured() } }

    func send() async {
        guard settings.isConfigured, let peripheralID = settings.peripheralID, !isSending else { return }
        isSending = true; status = "Collecting from ring…"
        defer { isSending = false }
        do {
            let captured = try await collector.collect(peripheralID: peripheralID, ringID: settings.ringID.trimmingCharacters(in: .whitespacesAndNewlines))
            await queue.append(captured); let pending = await queue.pending(); samples = captured
            status = "Sending \(pending.count) samples…"
            let ack = try await api.upload(settings: settings, samples: pending)
            await queue.remove(ids: Set(ack.accepted + ack.duplicates))
            let value = SendReceipt(date: Date(), sent: pending.count, accepted: ack.accepted.count,
                duplicates: ack.duplicates.count, rejected: ack.rejected.count)
            receipt = value; store.saveReceipt(value)
            status = "Sent \(value.sent): \(value.accepted) new, \(value.duplicates) duplicate, \(value.rejected) rejected"
        } catch { status = "Error: \(error.localizedDescription)" }
    }

    private func scheduleTimer() {
        timerTask?.cancel(); guard settings.isConfigured else { return }
        timerTask = Task { [weak self] in
            while !Task.isCancelled {
                let minutes = self?.settings.syncIntervalMinutes ?? 10
                try? await Task.sleep(for: .seconds(Double(minutes * 60)))
                if !Task.isCancelled { await self?.send() }
            }
        }
    }
}
