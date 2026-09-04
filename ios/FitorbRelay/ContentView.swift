import SwiftUI

struct ContentView: View {
    @ObservedObject var model: RelayViewModel

    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.035, blue: 0.03).ignoresSafeArea()
            if model.showingSetup { setup } else { dashboard }
        }
        .tint(.green)
        .buttonStyle(RelayButtonStyle())
    }

    private var setup: some View {
        NavigationStack {
            Form {
                Section("Ring") {
                    Button { Task { await model.scan() } } label: {
                        HStack {
                            if model.isScanning { ProgressView().controlSize(.small) }
                            Text(model.isScanning ? "Scanning…" : (model.settings.ringName.isEmpty ? "Scan for ring" : "Scan again"))
                        }
                    }
                    .disabled(model.isScanning)
                    .accessibilityHint("Searches for nearby Bluetooth devices for eight seconds")
                    Text("Nearby Bluetooth devices appear below. Choose your ring; the closest device usually has the strongest signal.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if model.isShowingScanResults {
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
                            .accessibilityLabel(ring.name)
                            .accessibilityValue(model.settings.peripheralID == ring.id ? "Selected, signal \(ring.rssi) decibels" : "Signal \(ring.rssi) decibels")
                            .accessibilityHint("Selects this Bluetooth device as your ring")
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
                pairingNotice
                connectionFields
                Section("Schedule while open") {
                    Stepper("Every \(model.settings.syncIntervalMinutes) minutes", value: $model.settings.syncIntervalMinutes, in: 1...60)
                }
                healthKitSection
                Section {
                    Text(model.status)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("Relay status")
                        .accessibilityValue(model.status)
                }
                if !model.settings.configurationIssues.isEmpty {
                    Section("Needed before sending") {
                        ForEach(model.settings.configurationIssues, id: \.self) { issue in
                            Label(issue, systemImage: "exclamationmark.circle")
                                .foregroundStyle(.orange)
                        }
                    }
                }
                Section {
                    Button {
                        Task {
                            await model.save()
                            await model.send()
                        }
                    } label: {
                        HStack {
                            if model.isSending { ProgressView().controlSize(.small) }
                            Text(model.isSending ? "Sending…" : "Save and Send")
                        }
                    }
                    .disabled(!model.settings.isConfigured || model.isSending)
                    .accessibilityHint("Saves these settings, collects ring data, and sends it to Home Assistant")
                }
            }
            .navigationTitle("Fitorb Relay Setup")
        }
    }

    private var dashboard: some View {
        TabView {
            NavigationStack {
                List {
                    pairingNotice
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
                        Button { Task { await model.send() } } label: {
                            HStack {
                                if model.isSending { ProgressView().controlSize(.small) }
                                Text(model.isSending ? "Sending…" : "Send")
                            }
                        }
                            .disabled(model.isSending)
                            .accessibilityHint("Collects current ring data and sends it to Home Assistant")
                    }
                }.navigationTitle("Fitorb Relay")
            }.tabItem { Label("Home", systemImage: "circle.hexagongrid") }
            NavigationStack {
                Form {
                    connectionFields
                    Section("Schedule while open") {
                        Stepper("Every \(model.settings.syncIntervalMinutes) minutes", value: $model.settings.syncIntervalMinutes, in: 1...60)
                    }
                    healthKitSection
                    Section {
                        Button("Save") { Task { await model.save() } }
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

    private var pairingNotice: some View {
        Section {
            Label {
                Text("If the ring asks to pair, choose Cancel or wait about 15 seconds. Pairing is not required for collection; accepting it can reduce battery life.")
                    .font(.subheadline)
            } icon: {
                Image(systemName: "bolt.horizontal.circle.fill")
                    .foregroundStyle(.orange)
            }
            .accessibilityLabel("Bluetooth pairing notice")
            .accessibilityValue("Cancel or ignore a ring pairing request. It clears after about 15 seconds. Pairing is not needed and can reduce battery life.")
        }
    }

    private var healthKitSection: some View {
        Section("Apple Health") {
            Toggle("Save ring data to Apple Health", isOn: Binding(
                get: { model.healthKitEnabled },
                set: { value in Task { await model.setHealthKitEnabled(value) } }
            ))
            .accessibilityHint("Shares supported ring measurements after each collection")
            Text(model.healthKitStatus).foregroundStyle(.secondary)
            Text("Saves heart rate, blood oxygen, completed-day activity, and sleep stages. Stress remains in Home Assistant.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private var latestByMetric: [RelaySample] {
        Dictionary(grouping: model.samples, by: \.metric).compactMap { $0.value.last }.sorted { $0.metric < $1.metric }
    }
    private func label(_ metric: String) -> String { metric.replacingOccurrences(of: "_", with: " ").capitalized }
}

private struct RelayButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.96 : 1)
            .opacity(configuration.isPressed ? 0.72 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
