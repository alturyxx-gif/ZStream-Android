import SwiftUI

struct RootView: View {
    @State private var tabConfig: TabBar.Config = .init(activeTab: 0)
    
    let buttons: [String] = ["house", "popcorn.circle", "person", "magnifyingglass" ]

    var body: some View {
        if #available (iOS 26.0, *) {
            TabBar(
                tabs: [
                    .init(symbol: "house"),
                    .init(symbol: "popcorn.circle"),
                    .init(symbol: "person")
                ],
                config: $tabConfig
            )
        }
        else {

        }
    }
}

#Preview {
    RootView()
}
