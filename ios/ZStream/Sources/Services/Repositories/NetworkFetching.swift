//
//  NetworkFetching.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// Generic http error
enum APIError: Error {
    case badResponse
}

/// Wrapper for http calls + json decode
func fetchPage<T: Decodable>(_ type: T.Type, from url: URL) async throws -> [T] {
    let (data, response) = try await URLSession.shared.data(from: url)
    guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
        throw APIError.badResponse
    }
    return try JSONDecoder().decode(PagedResponse<T>.self, from: data).results
}
