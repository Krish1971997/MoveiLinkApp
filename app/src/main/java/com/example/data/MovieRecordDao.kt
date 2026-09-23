package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieRecordDao {
    @Query("SELECT * FROM movies ORDER BY id DESC")
    fun getAllMovies(): Flow<List<MovieRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: MovieRecord)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM movies")
    suspend fun clearAll()
}
