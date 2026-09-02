import SwiftUI

struct ContentView: View {
    @ObservedObject var model: RelayViewModel

    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.035, blue: 0.03).ignoresSafeArea()
            if model.showingSetup { setup } else { dashboard }
        }
        .tint(.green)
    }

    private var setup: some View {
        NavigationStack {
            Form {
                Section("Ring") {
                    Button("Scan for ring") { Task { await model.scan() } }
                    ForEach(model.rings) { ring in
                        Button { model.choose(ring) } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(ring.name)
                                    Text(model.settings.peripheralID == ring.id ? "Selected" : "Tap to select")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(ring.rssi) dBm").foregroundStyle(.secondary)
                                if model.settings.peripheralID == ring.id {
                                    Image(systemName: "checkmark.circle.fill")
                                }
                            }
                        }
                    }
                    TextField("Home Assistant ring ID (Bluetooth MAC)", text: $model.settings.ringID)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    Text("Use the ring_id returned when Home Assistant created the relay token. iOS does not expose the ring's MAC address.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if !model.settings.ringName.isEmpty { Text("Selected: \(model.settings.ringName)") }
                }
                connectionFields
                Section("Schedule while open") {
                    Stepper("Every \(model.settings.syncIntervalMinutes) minutes", value: $model.settings.syncIntervalMinutes, in: 1...60)
                }
                Section { Text(model.status).foregroundStyle(.secondary) }
                if !model.settings.configurationIssues.isEmpty {
                    Section("Needed before sending") {
                        ForEach(model.settings.configurationIssues, id: \.self) { issue in
                            Label(issue, systemImage: "exclamationmark.circle")
                                .foregroundStyle(.orange)
                        }
                    }
                }
                Section {
                    Button(model.isSending ? "Sending…" : "Save and Send") {
                        model.save(); Task { await model.send() }
                    }.disabled(!model.settings.isConfigured || model.isSending)
                }
            }
            .navigationTitle("Fitorb Relay Setup")
        }
    }

    private var dashboard: some View {
        TabView {
            NavigationStack {
                List {
                    Section("Relay") {
                        LabeledContent("Ring", value: model.settings.ringName)
                        LabeledContent("Status", value: model.status)
                        if let receipt = model.receipt {
                            LabeledContent("Last sent", value: "\(receipt.sent) samples")
                            LabeledContent("Accepted", value: "\(receipt.accepted)")
                            LabeledContent("Duplicates", value: "\(receipt.duplicates)")
                            LabeledContent("Rejected", value: "\(receipt.rejected)")
                            LabeledContent("Time", value: receipt.date.formatted(date: .abbreviated, time: .shortened))
                        }
                    }
                    Section("Latest ring data") {
                        ForEach(latestByMetric) { sample in
                            LabeledContent(label(sample.metric), value: sample.value.display + (sample.unit.map { " \($0)" } ?? ""))
                        }
                    }
                    Section {
                        Button(model.isSending ? "Sending…" : "Send") { Task { await model.send() } }
                            .disabled(model.isSending)
                    }
                }.navigationTitle("Fitorb Relay")
            }.tabItem { Label("Home", systemImage: "circle.hexagongrid") }
            NavigationStack {
                Form {
                    connectionFields
                    Section("Schedule while open") {
                        Stepper("Every \(model.settings.syncIntervalMinutes) minutes", value: $model.settings.syncIntervalMinutes, in: 1...60)
                    }
                    Section {
                        Button("Save") { model.save() }
                        Button("Run setup again") { model.showingSetup = true }
                    }
                }.navigationTitle("Settings")
            }.tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }

    private var connectionFields: some View {
        Section("Home Assistant") {
            TextField("URL", text: $model.settings.homeAssistantURL)
                .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
            SecureField("Relay token", text: $model.settings.relayToken)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Relay ID", text: $model.settings.relayID)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
        }
    }

    private var latestByMetric: [RelaySample] {
        Dictionary(grouping: model.samples, by: \.metric).compactMap { $0.value.last }.sorted { $0.metric < $1.metric }
    }
    private func label(_ metric: String) -> String { metric.replacingOccurrences(of: "_", with: " ").capitalized }
}
