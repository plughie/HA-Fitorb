import XCTest
@testable import FitorbRelay

final class RingProtocolTests: XCTestCase {
    func testConfigurationExplainsEveryMissingField() {
        let settings = RelaySettings()
        XCTAssertTrue(settings.configurationIssues.contains("Select the scanned ring"))
        XCTAssertTrue(settings.configurationIssues.contains { $0.contains("Home Assistant ring ID") })
        XCTAssertTrue(settings.configurationIssues.contains { $0.contains("Home Assistant URL") })
        XCTAssertTrue(settings.configurationIssues.contains("Paste a Fitorb relay token"))
    }

    func testConfigurationNormalizesPastedValues() {
        var settings = RelaySettings(
            homeAssistantURL: " https://ha.example.test/ ",
            relayToken: " fitorb_relay_example ",
            relayID: " my-iphone ",
            ringID: " aa:bb:cc:dd:ee:ff ",
            ringName: " R12 ",
            peripheralID: UUID(),
            syncIntervalMinutes: 10
        )
        settings.normalize()
        XCTAssertTrue(settings.isConfigured)
        XCTAssertEqual(settings.homeAssistantURL, "https://ha.example.test")
        XCTAssertEqual(settings.ringID, "AA:BB:CC:DD:EE:FF")
    }

    func testCommandChecksum() {
        let packet = [UInt8](RingProtocol.command([0x69, 0x03, 0x01]))
        XCTAssertEqual(packet.count, 16)
        XCTAssertEqual(packet[15], 0x6d)
    }

    func testBatteryPacketCreatesBatteryAndChargingSamples() {
        var packet = [UInt8](repeating: 0, count: 16); packet[0] = 3; packet[1] = 88; packet[2] = 1
        let samples = RingProtocol.currentSamples(Data(packet), ringID: "AA:BB:CC:DD:EE:FF")
        XCTAssertEqual(samples.map(\.metric), ["battery", "charging"])
        XCTAssertEqual(samples[0].value, .int(88))
        XCTAssertEqual(samples[1].value, .bool(true))
    }

    func testSpo2HealthPacket() {
        var packet = [UInt8](repeating: 0, count: 16); packet[0] = 0x69; packet[1] = 3; packet[3] = 99
        XCTAssertEqual(RingProtocol.healthSample(Data(packet), ringID: "ring", type: 3, metric: "spo2", unit: "%")?.value, .int(99))
    }

    func testActivityPacketsAreAccumulated() {
        var parser = ActivityAccumulator()
        var header = [UInt8](repeating: 0, count: 16)
        header[0] = 0x43; header[1] = 0xf0; header[3] = 1
        XCTAssertNil(parser.consume(Data(header), ringID: "ring"))

        var packet = [UInt8](repeating: 0, count: 16)
        packet[0] = 0x43; packet[2] = 0x09; packet[3] = 0x02
        packet[5] = 0; packet[6] = 1
        packet[7] = 0x7b; packet[8] = 0
        packet[9] = 0xd2; packet[10] = 0x04
        packet[11] = 0x41; packet[12] = 0x01
        let samples = parser.consume(Data(packet), ringID: "ring")

        XCTAssertEqual(samples?.map(\.metric), ["steps", "calories", "distance"])
        XCTAssertEqual(samples?[0].value, .int(1234))
        XCTAssertEqual(samples?[1].value, .int(1))
        XCTAssertEqual(samples?[2].value, .int(321))
    }
}
