import Foundation
import HealthKit

actor HealthKitExporter {
    private let healthStore = HKHealthStore()
    private let defaults = UserDefaults.standard
    private let exportedKey = "healthkit.exportedSampleIDs.v1"

    static var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    private var writableTypes: Set<HKSampleType> {
        let quantityIDs: [HKQuantityTypeIdentifier] = [
            .heartRate, .oxygenSaturation, .stepCount, .distanceWalkingRunning, .activeEnergyBurned,
        ]
        var types = Set<HKSampleType>(quantityIDs.compactMap(HKQuantityType.quantityType(forIdentifier:)))
        if let sleep = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(sleep) }
        return types
    }

    func requestAuthorization() async throws -> Bool {
        guard Self.isAvailable else { return false }
        try await healthStore.requestAuthorization(toShare: writableTypes, read: [])
        return true
    }

    func export(_ relaySamples: [RelaySample]) async throws -> Int {
        guard Self.isAvailable else { return 0 }
        let exported = Set(defaults.stringArray(forKey: exportedKey) ?? [])
        let pending = relaySamples.filter { !exported.contains($0.sampleID) }
        let mapped = pending.compactMap(makeSample)
        guard !mapped.isEmpty else { return 0 }
        try await healthStore.save(mapped.map(\.sample))
        var updated = Array(exported.union(mapped.map(\.sampleID)))
        if updated.count > 5_000 { updated = Array(updated.suffix(5_000)) }
        defaults.set(updated, forKey: exportedKey)
        return mapped.count
    }

    private func makeSample(_ relay: RelaySample) -> (sampleID: String, sample: HKSample)? {
        guard let date = ISO8601DateFormatter().date(from: relay.timestamp),
              let number = relay.value.number else { return makeSleepSample(relay) }
        let metadata: [String: Any] = [
            HKMetadataKeyExternalUUID: relay.sampleID,
            HKMetadataKeyDeviceSerialNumber: relay.ringID,
        ]
        let quantity: (HKQuantityTypeIdentifier, HKUnit, Double)? = switch relay.metric {
        case "heart_rate": (.heartRate, HKUnit.count().unitDivided(by: .minute()), number)
        case "spo2": (.oxygenSaturation, .percent(), number / 100)
        case "steps" where relay.isCompletedDay: (.stepCount, .count(), number)
        case "distance" where relay.isCompletedDay: (.distanceWalkingRunning, .meter(), number)
        case "calories" where relay.isCompletedDay: (.activeEnergyBurned, .kilocalorie(), number)
        default: nil
        }
        guard let quantity, number >= 0,
              let type = HKQuantityType.quantityType(forIdentifier: quantity.0) else { return nil }
        let end = relay.isDailyTotal ? Calendar.current.date(byAdding: .day, value: 1, to: date) ?? date : date
        return (relay.sampleID, HKQuantitySample(type: type, quantity: HKQuantity(unit: quantity.1, doubleValue: quantity.2), start: date, end: end, metadata: metadata))
    }

    private func makeSleepSample(_ relay: RelaySample) -> (sampleID: String, sample: HKSample)? {
        guard relay.metric == "sleep_stage",
              case .string(let stage) = relay.value,
              let start = ISO8601DateFormatter().date(from: relay.timestamp),
              let minutes = relay.sleepStageMinutes,
              let type = HKCategoryType.categoryType(forIdentifier: .sleepAnalysis) else { return nil }
        let value: HKCategoryValueSleepAnalysis = switch stage {
        case "awake": .awake
        case "light": .asleepCore
        case "deep": .asleepDeep
        case "rem": .asleepREM
        default: .asleepUnspecified
        }
        let metadata: [String: Any] = [
            HKMetadataKeyExternalUUID: relay.sampleID,
            HKMetadataKeyDeviceSerialNumber: relay.ringID,
            HKMetadataKeyTimeZone: TimeZone.current.identifier,
        ]
        return (relay.sampleID, HKCategorySample(type: type, value: value.rawValue, start: start,
            end: start.addingTimeInterval(Double(minutes * 60)), metadata: metadata))
    }
}

private extension RelayValue {
    var number: Double? {
        switch self {
        case .int(let value): Double(value)
        case .double(let value): value
        case .string(let value): Double(value)
        case .bool: nil
        }
    }
}

private extension RelaySample {
    var isDailyTotal: Bool { ["steps", "distance", "calories"].contains(metric) }
    var isCompletedDay: Bool {
        guard let localDate, let day = ISO8601DateFormatter.day.date(from: localDate) else { return false }
        return day < Calendar.current.startOfDay(for: Date())
    }

    var sleepStageMinutes: Int? {
        guard let rawHex, rawHex.count >= 4 else { return nil }
        return Int(rawHex.dropFirst(2).prefix(2), radix: 16)
    }
}
