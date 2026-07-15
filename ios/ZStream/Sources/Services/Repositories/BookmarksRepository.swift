//
//  BookmarksRepository.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// Cache backed loader for user bookmarks 
struct BookmarksRepository: CacheBackedLoader {
    let cache: DiskCache = .shared
    let cacheKey = "bookmarks"
    let ttl: TimeInterval = .infinity

    func fetchFromNetwork() async throws -> [Bookmark] {
        [] // TODO: fetch user bookmarks from Celeste backend
    }

    func add(_ bookmark: Bookmark) async {
        var current = (await cache.read([Bookmark].self, key: cacheKey))?.value ?? []
        guard !current.contains(where: { $0.id == bookmark.id && $0.mediaType == bookmark.mediaType }) else { return }
        current.append(bookmark)
        await cache.write(current, key: cacheKey)
    }

    func remove(id: Int, mediaType: MediaType) async {
        var current = (await cache.read([Bookmark].self, key: cacheKey))?.value ?? []
        current.removeAll { $0.id == id && $0.mediaType == mediaType }
        await cache.write(current, key: cacheKey)
    }
}
