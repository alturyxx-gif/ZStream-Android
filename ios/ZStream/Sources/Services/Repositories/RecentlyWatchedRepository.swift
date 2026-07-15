//
//  RecentlyWatchedRepository.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// Cache Backed loader for user recently watched medias
struct RecentlyWatchedRepository: CacheBackedLoader {
    let cache: DiskCache = .shared
    let cacheKey = "recently_watched"
    let ttl: TimeInterval = .infinity

    func fetchFromNetwork() async throws -> [RecentlyWatched] {
        [] // TODO: fetch user recently watched from Celeste backend
    }

    func markWatched(_ item: RecentlyWatched) async {
        var current = (await cache.read([RecentlyWatched].self, key: cacheKey))?.value ?? []
        current.removeAll { $0.id == item.id && $0.mediaType == item.mediaType }
        current.insert(item, at: 0)
        await cache.write(current, key: cacheKey)
    }
}
