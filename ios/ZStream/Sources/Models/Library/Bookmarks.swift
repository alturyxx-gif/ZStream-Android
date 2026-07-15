//
//  Bookmarks.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// A bookmark added by the user
struct Bookmark: Codable, Identifiable, Equatable {
    let id: Int
    let mediaType: MediaType
    let addedAt: Date
}
