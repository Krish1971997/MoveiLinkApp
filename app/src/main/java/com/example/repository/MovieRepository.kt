package com.example.repository

import com.example.data.MovieRecord
import com.example.data.MovieRecordDao
import kotlinx.coroutines.flow.Flow

class MovieRepository(private val movieDao: MovieRecordDao) {
    val allMovies: Flow<List<MovieRecord>> = movieDao.getAllMovies()

    suspend fun insertAll(movies: List<MovieRecord>) {
        movieDao.insertAll(movies)
    }

    suspend fun insert(movie: MovieRecord) {
        movieDao.insert(movie)
    }

    suspend fun deleteById(id: Int) {
        movieDao.deleteById(id)
    }

    suspend fun clearAll() {
        movieDao.clearAll()
    }
}
