import SwiftUI

// This is just a test for me to try out liquid glass and how to use it, not a real view
struct TabBar: View {
    var tabs: [TabItem]
    @Binding var config: Config
    
    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: 10) {
                CustomTabBar(tabs: tabs, activeTab: $config.activeTab)
                    .frame(width: CGFloat(tabs.count) * 60, height: 45)
                    .glassEffect(
                        .regular.interactive(),
                        in: .capsule
                    )
            }
        } else { }
    }

    struct Config {
        var activeTab: Int
    }

    struct TabItem {
        var symbol: String
    }
}

@available(iOS 26.0, *)
fileprivate struct CustomTabBar: UIViewRepresentable {
    var tabs: [TabBar.TabItem]
    @Binding var activeTab: Int

    func makeUIView(context: Context) -> UISegmentedControl {
        let control = UISegmentedControl(items: tabs.compactMap({ $0.symbol }))
        let font = UIFont.preferredFont(forTextStyle: .body)

        for (index, item) in tabs.enumerated() {
            let image = UIImage(
                systemName: item.symbol,
                withConfiguration: UIImage.SymbolConfiguration(font: font)
            )

            control.setImage(image, forSegmentAt: index)
        }

        control.addTarget(
            context.coordinator,
            action: #selector(Coordinator.didItemChanged(_:)),
            for: .valueChanged
        )

        return control
    }

    func updateUIView(_ uiView: UISegmentedControl, context: Context) {
        if uiView.selectedSegmentIndex != activeTab {
            uiView.selectedSegmentIndex = activeTab
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UISegmentedControl, context: Context) -> CGSize? {
        return .init(width: proposal.width ?? 0, height: proposal.height ?? 0)
    }
    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    class Coordinator: NSObject {
        var parent: CustomTabBar

        init(parent: CustomTabBar) {
            self.parent = parent
        }

        @objc
        func didItemChanged(_ control: UISegmentedControl) {
            parent.activeTab = control.selectedSegmentIndex
        }
    }
}
