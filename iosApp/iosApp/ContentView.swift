import UIKit
import SwiftUI
import WidgetKit
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        // Kotlin/Native has no WidgetKit binding, so the shared Compose shell
        // can't reload timelines itself - it calls back out here after the
        // mascot selection is saved. Mirrors WidgetlyApplication's
        // refreshMascotWidgets() on Android (both Glance widgets get pushed);
        // reloadAllTimelines covers DdayWidget and ConnectionWidget alike, and
        // matches the existing reloadTimelines(ofKind:) precedent in
        // SignalNotificationService/NotificationService.swift.
        MainViewControllerKt.MainViewController(onMascotChanged: {
            WidgetCenter.shared.reloadAllTimelines()
        })
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}