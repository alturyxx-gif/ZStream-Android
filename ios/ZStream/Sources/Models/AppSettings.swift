//
//  AppSettings.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// Users preferences that affect API calls
final class AppSettings: ObservableObject {
    @Published var locale: String = "en-US"
    @Published var kidMode: Bool = false
}
