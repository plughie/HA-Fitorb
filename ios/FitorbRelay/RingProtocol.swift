import CryptoKit
import CoreBluetooth
import Foundation

enum RingProtocol {
    static let uartService = CBUUID(string: "6E40FFF0-B5A3-F393-E0A9-E50E24DCCA9E")
    static let uartWrite = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    static let uartNotify = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    static let dataService = CBUUID(string: "DE5BF728-D711-4E47-AF26-65E3012A5DC7")
    static let dataWrite = CBUUID(string: "DE5BF72A-D711-4E47-AF26-65E3012A5DC7")
    static let dataNotify = CBUUID(string: "DE5BF729-D711-4E47-AF26-65E3012A5DC7")

    static func command(_ bytes: [UInt8]) -> Data {
        var packet = bytes + Array(repeating: 0, count: max(0, 16 - bytes.count))
        packet = Array(packet.prefix(16)); packet[15] = UInt8(packet.prefix(15).reduce(0) { ($0 + Int($1)) & 0xff })
        return Data(packet)
    }

    static func sample(ringID: String, metric: String, value: RelayValue, unit: String? = nil,
                       date: Date = Date(), localDate: String? = nil, raw: Data? = nil) -> RelaySample {
        let stamp = ISO8601DateFormatter().string(from: date)
        let valueText = value.display
        let digest = SHA256.hash(data: Data("\(ringID)|\(metric)|\(stamp)|\(valueText)".utf8))
            .prefix(12).map { String(format: "%02x", $0) }.joined()
        return RelaySample(sampleID: "ir-\(digest)", ringID: ringID, metric: metric, timestamp: stamp,
            value: value, unit: unit, capturedAt: stamp, localDate: localDate,
            rawHex: raw?.map { String(format: "%02x", $0) }.joined())
    }

    static func currentSamples(_ data: Data, ringID: String) -> [RelaySample] {
        let b = [UInt8](data); guard b.count == 16 else { return [] }
        if b[0] == 0x03 { return [sample(ringID: ringID, metric: "battery", value: .int(Int(b[1])), unit: "%"),
            sample(ringID: ringID, metric: "charging", value: .bool(b[2] == 1))] }
        if b[0] == 0x73 && b[1] == 0x12 {
            let steps = Int(b[2]) << 16 | Int(b[3]) << 8 | Int(b[4])
            let calories = (Int(b[5]) << 16 | Int(b[6]) << 8 | Int(b[7])) / 1000
            let distance = Int(b[8]) << 16 | Int(b[9]) << 8 | Int(b[10])
            return [sample(ringID: ringID, metric: "steps", value: .int(steps)),
                sample(ringID: ringID, metric: "calories", value: .int(calories), unit: "kcal"),
                sample(ringID: ringID, metric: "distance", value: .int(distance), unit: "m")]
        }
        return []
    }

    static func healthSample(_ data: Data, ringID: String, type: UInt8, metric: String, unit: String?) -> RelaySample? {
        let b = [UInt8](data); guard b.count == 16, b[0] == 0x69, b[1] == type, b[3] > 0 else { return nil }
        return sample(ringID: ringID, metric: metric, value: .int(Int(b[3])), unit: unit,
            localDate: ISO8601DateFormatter.day.string(from: Date()))
    }

    static func sleepSamples(_ payload: Data, ringID: String) -> [RelaySample] {
        let b = [UInt8](payload); guard !b.isEmpty else { return [] }; var offset = 1; var result: [RelaySample] = []
        for _ in 0..<Int(b[0]) {
            guard offset + 6 <= b.count else { break }
            let daysAgo = Int(b[offset]), length = Int(b[offset + 1]), end = offset + 2 + length
            guard length >= 4, end <= b.count else { break }
            let startMinute = Int(Int16(bitPattern: UInt16(b[offset + 2]) | UInt16(b[offset + 3]) << 8))
            let endMinute = Int(Int16(bitPattern: UInt16(b[offset + 4]) | UInt16(b[offset + 5]) << 8))
            let duration = endMinute <= startMinute ? 1440 - startMinute + endMinute : endMinute - startMinute
            let day = Calendar.current.date(byAdding: .day, value: -daysAgo, to: Date()) ?? Date()
            let dayText = ISO8601DateFormatter.day.string(from: day)
            let start = Calendar.current.date(
                byAdding: .minute,
                value: startMinute,
                to: Calendar.current.startOfDay(for: day)
            ) ?? day
            var awake = 0, light = 0, deep = 0, rem = 0
            var periodOffset = offset + 6, elapsed = 0
            while periodOffset + 1 < end {
                let stageRaw = b[periodOffset], minutes = Int(b[periodOffset + 1])
                let stage: String?
                switch stageRaw {
                case 2: light += minutes; stage = "light"
                case 3: deep += minutes; stage = "deep"
                case 4: rem += minutes; stage = "rem"
                case 5: awake += minutes; stage = "awake"
                default: stage = nil
                }
                if let stage, let stageDate = Calendar.current.date(byAdding: .minute, value: elapsed, to: start) {
                    result.append(sample(ringID: ringID, metric: "sleep_stage", value: .string(stage),
                        date: stageDate, localDate: dayText, raw: Data([stageRaw, UInt8(minutes)])))
                }
                elapsed += minutes
                periodOffset += 2
            }
            let values = [("sleep_summary", duration), ("sleep_asleep", max(0, duration - awake)),
                ("sleep_awake", awake), ("sleep_light", light), ("sleep_deep", deep), ("sleep_rem", rem)]
            result += values.map { sample(ringID: ringID, metric: $0.0, value: .int($0.1), unit: "min", date: start, localDate: dayText) }
            offset = end
        }
        return result
    }
}

struct ActivityAccumulator {
    private var newCalorieProtocol = false
    private var steps = 0
    private var caloriesRaw = 0
    private var distance = 0

    mutating func consume(_ data: Data, ringID: String, date: Date = Date()) -> [RelaySample]? {
        let b = [UInt8](data)
        guard b.count == 16, b[0] == 0x43 else { return nil }
        if b[1] == 0xff {
            reset()
            return samples(ringID: ringID, date: date)
        }
        if b[1] == 0xf0 {
            newCalorieProtocol = b[3] == 1
            return nil
        }
        let month = Int((b[2] >> 4) * 10 + (b[2] & 0x0f))
        let day = Int((b[3] >> 4) * 10 + (b[3] & 0x0f))
        guard (1...12).contains(month), (1...31).contains(day) else { return nil }
        var calories = Int(b[7]) | Int(b[8]) << 8
        if newCalorieProtocol { calories *= 10 }
        caloriesRaw += calories
        steps += Int(b[9]) | Int(b[10]) << 8
        distance += Int(b[11]) | Int(b[12]) << 8
        guard b[6] > 0, b[5] == b[6] - 1 else { return nil }
        let result = samples(ringID: ringID, date: date)
        reset()
        return result
    }

    private func samples(ringID: String, date: Date) -> [RelaySample] {
        let sampleDate = Calendar.current.startOfDay(for: date)
        let localDate = ISO8601DateFormatter.day.string(from: sampleDate)
        return [
            RingProtocol.sample(ringID: ringID, metric: "steps", value: .int(steps), date: sampleDate, localDate: localDate),
            RingProtocol.sample(ringID: ringID, metric: "calories", value: .int(caloriesRaw / 1000), unit: "kcal", date: sampleDate, localDate: localDate),
            RingProtocol.sample(ringID: ringID, metric: "distance", value: .int(distance), unit: "m", date: sampleDate, localDate: localDate),
        ]
    }

    private mutating func reset() {
        newCalorieProtocol = false
        steps = 0
        caloriesRaw = 0
        distance = 0
    }
}

extension ISO8601DateFormatter {
    static let day: DateFormatter = { let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f }()
}
