@preconcurrency import CoreBluetooth
import Foundation

@MainActor
final class RingCollector: NSObject, @preconcurrency CBCentralManagerDelegate, @preconcurrency CBPeripheralDelegate {
    private var central: CBCentralManager!
    private var discovered: [UUID: RingChoice] = [:]
    private var peripheral: CBPeripheral?
    private var uartWrite: CBCharacteristic?
    private var dataWrite: CBCharacteristic?
    private var uartNotificationsEnabled = false
    private var dataNotificationsEnabled = false
    private var uartPackets: [Data] = []
    private var dataPackets: [Data] = []

    override init() { super.init(); central = CBCentralManager(delegate: self, queue: .main) }
    func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    func scan() async throws -> [RingChoice] {
        try await waitUntil { self.central.state == .poweredOn }
        discovered = [:]
        for peripheral in central.retrieveConnectedPeripherals(withServices: [RingProtocol.uartService, RingProtocol.dataService]) {
            let name = peripheral.name ?? "Connected ring"
            discovered[peripheral.identifier] = RingChoice(id: peripheral.identifier, name: name, rssi: 0)
        }
        central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        try await Task.sleep(for: .seconds(8)); central.stopScan()
        return discovered.values.sorted { $0.rssi > $1.rssi }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let advertisedName = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name
        let suffix = peripheral.identifier.uuidString.prefix(4)
        let name = advertisedName?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "Unnamed Bluetooth device · \(suffix)"
        discovered[peripheral.identifier] = RingChoice(id: peripheral.identifier, name: name, rssi: RSSI.intValue)
    }

    func collect(peripheralID: UUID, ringID: String) async throws -> [RelaySample] {
        try await waitUntil { self.central.state == .poweredOn }
        guard let ring = central.retrievePeripherals(withIdentifiers: [peripheralID]).first else { throw RelayError.message("Ring must be scanned again") }
        peripheral = ring; ring.delegate = self; central.connect(ring)
        defer {
            central.cancelPeripheralConnection(ring)
            peripheral = nil
            uartWrite = nil
            dataWrite = nil
            uartNotificationsEnabled = false
            dataNotificationsEnabled = false
            uartPackets.removeAll()
            dataPackets.removeAll()
        }
        try await waitUntil(timeout: 20) { ring.state == .connected }
        ring.discoverServices([RingProtocol.uartService, RingProtocol.dataService])
        // CoreBluetooth enables notifications asynchronously. Wait for the UART
        // subscription before sending requests, as Android does after its CCCD write.
        try await waitUntil(timeout: 12) { self.uartWrite != nil && self.uartNotificationsEnabled }
        var samples: [RelaySample] = []
        write(RingProtocol.command([0x03]), to: uartWrite)
        if let packet = try await nextUART(where: { $0.first == 0x03 }) { samples += RingProtocol.currentSamples(packet, ringID: ringID) }
        write(RingProtocol.command([0x43, 0, 0x0f, 0, 0x5f, 1]), to: uartWrite)
        var activity = ActivityAccumulator()
        let activityDeadline = Date().addingTimeInterval(15)
        while Date() < activityDeadline,
              let packet = try await nextUART(timeout: 2, where: { $0.first == 0x43 }) {
            if let activitySamples = activity.consume(packet, ringID: ringID) {
                samples += activitySamples
                break
            }
        }
        samples += try await health(type: 0x01, metric: "heart_rate", unit: "bpm", ringID: ringID)
        samples += try await health(type: 0x03, metric: "spo2", unit: "%", ringID: ringID)
        samples += try await health(type: 0x08, metric: "stress", unit: nil, ringID: ringID)
        // Sleep arrives over the Big Data notification characteristic. Do not make
        // the request until that subscription has been confirmed.
        if let dataWrite, (try? await waitUntil(timeout: 12) { self.dataNotificationsEnabled }) != nil {
            write(Data([0xbc, 0x27, 0, 0, 0xff, 0xff]), to: dataWrite)
            if let frame = try await nextBigData() { samples += RingProtocol.sleepSamples(frame, ringID: ringID) }
        }
        return samples
    }

    private func health(type: UInt8, metric: String, unit: String?, ringID: String) async throws -> [RelaySample] {
        defer { write(RingProtocol.command([0x6a, type, 0, 0]), to: uartWrite) }
        write(RingProtocol.command([0x69, type, 0x01]), to: uartWrite); try await Task.sleep(for: .milliseconds(500))
        write(RingProtocol.command([0x69, type, 0x03]), to: uartWrite)
        guard let packet = try await nextUART(timeout: 60, where: { RingProtocol.healthSample($0, ringID: ringID, type: type, metric: metric, unit: unit) != nil }),
              let sample = RingProtocol.healthSample(packet, ringID: ringID, type: type, metric: metric, unit: unit) else { return [] }
        return [sample]
    }

    private func write(_ data: Data, to characteristic: CBCharacteristic?) {
        guard let peripheral, let characteristic else { return }; peripheral.writeValue(data, for: characteristic, type: .withResponse)
    }

    private func nextUART(timeout: TimeInterval = 10, where predicate: (Data) -> Bool) async throws -> Data? {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if let index = uartPackets.firstIndex(where: predicate) { return uartPackets.remove(at: index) }
            try await Task.sleep(for: .milliseconds(100))
        }
        return nil
    }

    private func nextBigData() async throws -> Data? {
        var buffer = Data(), deadline = Date().addingTimeInterval(24)
        while Date() < deadline {
            if !dataPackets.isEmpty { buffer.append(dataPackets.removeFirst()) }
            while buffer.count >= 6 {
                if buffer[0] != 0xbc { buffer.removeFirst(); continue }
                let count = Int(buffer[2]) | Int(buffer[3]) << 8
                guard buffer.count >= 6 + count else { break }
                let id = buffer[1], payload = buffer.subdata(in: 6..<(6 + count)); buffer.removeSubrange(0..<(6 + count))
                if id == 0x27 { return payload }
            }
            try await Task.sleep(for: .milliseconds(100))
        }
        return nil
    }

    private func waitUntil(timeout: TimeInterval = 10, _ condition: @escaping () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() {
            switch central.state {
            case .poweredOff: throw RelayError.message("Bluetooth is turned off")
            case .unauthorized: throw RelayError.message("Bluetooth access is not allowed. Enable it in Settings → Privacy & Security → Bluetooth.")
            case .unsupported: throw RelayError.message("Bluetooth Low Energy is not supported on this device")
            default: break
            }
            if Date() >= deadline { throw RelayError.message("Bluetooth timed out") }
            try await Task.sleep(for: .milliseconds(100))
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) { peripheral.discoverServices(nil) }
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {}
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        peripheral.services?.forEach { peripheral.discoverCharacteristics(nil, for: $0) }
    }
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        service.characteristics?.forEach { c in
            switch c.uuid {
            case RingProtocol.uartWrite: uartWrite = c
            case RingProtocol.dataWrite: dataWrite = c
            case RingProtocol.uartNotify, RingProtocol.dataNotify: peripheral.setNotifyValue(true, for: c)
            default: break
            }
        }
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let value = characteristic.value else { return }
        if characteristic.uuid == RingProtocol.uartNotify { uartPackets.append(value) }
        if characteristic.uuid == RingProtocol.dataNotify { dataPackets.append(value) }
    }
    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard error == nil, characteristic.isNotifying else { return }
        if characteristic.uuid == RingProtocol.uartNotify { uartNotificationsEnabled = true }
        if characteristic.uuid == RingProtocol.dataNotify { dataNotificationsEnabled = true }
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
