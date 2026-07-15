//
//  Movie.swift
//  ZStream
//
//  Created by Francesco Macaluso on 7/15/26.
//

import Foundation

/// Decodes a TMDB movie response
struct Movie: MediaItem {
    let id: Int
    let adult: Bool
    let title: String
    let originalTitle: String
    let originalLanguage: String
    let overview: String
    let posterPath: String?
    let backdropPath: String?
    let releaseDate: String?
    let genreIds: [Int]
    let popularity: Double
    let voteAverage: Double
    let voteCount: Int
    let video: Bool

    var mediaType: MediaType { .movie }
    var displayTitle: String { title }
    var rating: Double { voteAverage }

    // Composed from path fragments
    var posterURL: URL? {
        guard let posterPath else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w500\(posterPath)")
    }

    // Composed from path fragments
    var backdropURL: URL? {
        guard let backdropPath else { return nil }
        return URL(string: "https://image.tmdb.org/t/p/w780\(backdropPath)")
    }

    var releaseYear: Int? {
        guard let releaseDate, releaseDate.count >= 4 else { return nil }
        return Int(releaseDate.prefix(4))
    }

    enum CodingKeys: String, CodingKey {
        case id, adult, title, overview, popularity, video
        case originalTitle = "original_title"
        case originalLanguage = "original_language"
        case posterPath = "poster_path"
        case backdropPath = "backdrop_path"
        case releaseDate = "release_date"
        case genreIds = "genre_ids"
        case voteAverage = "vote_average"
        case voteCount = "vote_count"
    }
}
