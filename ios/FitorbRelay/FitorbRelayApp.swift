import SwiftUI

@main
struct FitorbRelayApp: App {
    @StateObject private var model = RelayViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .preferredColorScheme(.dark)
                .task { await model.startIfConfigured() }
                .onChange(of: scenePhase) { phase in
                    if phase == .active { Task { await model.becameActive() } }
                }
        }
    }
}
