package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MovieRecord
import com.example.data.ZohoPreferences
import com.example.parser.FileImporter
import com.example.repository.MovieRepository
import com.example.repository.ZohoSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


sealed interface ImportingState {
    object Idle : ImportingState
    object Loading : ImportingState
    data class Success(val count: Int) : ImportingState
    data class Error(val message: String) : ImportingState
}

class MovieViewModel(private val repository: MovieRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _importingState = MutableStateFlow<ImportingState>(ImportingState.Idle)
    val importingState: StateFlow<ImportingState> = _importingState.asStateFlow()

    val categories: StateFlow<List<String>> = repository.allMovies.map { movies ->
        movies.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMovies: StateFlow<List<MovieRecord>> = repository.allMovies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val filteredMovies: StateFlow<List<MovieRecord>> = combine(
        repository.allMovies,
        _searchQuery,
        _selectedCategory
    ) { movies, query, category ->
        val filtered = movies.filter { movie ->
            val matchesCategory = category == null || movie.category.equals(category, ignoreCase = true)
            val matchesSearch = query.isEmpty() ||
                    movie.name.contains(query, ignoreCase = true) ||
                    movie.category.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Preload sample movies if the database is empty
        viewModelScope.launch(Dispatchers.IO) {
            repository.allMovies.first().let { items ->
                if (items.isEmpty()) {
                    val sampleMovies = listOf(
                        MovieRecord(
                            name = "Dacoit A Love Story (2026)",
                            sublink = "/dacoit-a-love-story-2026-tamil-movie/",
                            category = "tamil-2026-movies",
                            link = "https://moviesda30.com/dacoit-a-love-story-2026-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2026-movies/"
                        ),
                        MovieRecord(
                            name = "Vaazha II Biopic of a Billion Bros (2026)",
                            sublink = "/vaazha-ii-biopic-of-a-billion-bros-2026-tamil-movie/",
                            category = "tamil-2026-movies",
                            link = "https://moviesda30.com/vaazha-ii-biopic-of-a-billion-bros-2026-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2026-movies/"
                        ),
                        MovieRecord(
                            name = "Coolie (2026)",
                            sublink = "/coolie-2026-tamil-movie/",
                            category = "tamil-2026-movies",
                            link = "https://moviesda30.com/coolie-2026-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2026-movies/"
                        ),
                        MovieRecord(
                            name = "Thalapathy 69 (2026)",
                            sublink = "/thalapathy-69-2026-tamil-movie/",
                            category = "tamil-2026-movies",
                            link = "https://moviesda30.com/thalapathy-69-2026-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2026-movies/"
                        ),
                        MovieRecord(
                            name = "Amaran (2024)",
                            sublink = "/amaran-2024-tamil-movie/",
                            category = "tamil-2024-movies",
                            link = "https://moviesda30.com/amaran-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2024-movies/"
                        ),
                        MovieRecord(
                            name = "Vettaiyan (2024)",
                            sublink = "/vettaiyan-2024-tamil-movie/",
                            category = "tamil-2024-movies",
                            link = "https://moviesda30.com/vettaiyan-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2024-movies/"
                        ),
                        MovieRecord(
                            name = "The Greatest Of All Time (2024)",
                            sublink = "/the-greatest-of-all-time-tamil-movie/",
                            category = "tamil-2024-movies",
                            link = "https://moviesda30.com/the-greatest-of-all-time-tamil-movie/",
                            pageUrl = "https://moviesda30.com/tamil-2024-movies/"
                        )
                    )
                    repository.insertAll(sampleMovies)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun resetImportState() {
        _importingState.value = ImportingState.Idle
    }

    fun importFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importingState.value = ImportingState.Loading
            val records = kotlinx.coroutines.withContext(Dispatchers.IO) {
                FileImporter.importUri(context, uri)
            }
            if (records.isNotEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val prefs = ZohoPreferences(context)
                    if (prefs.clearOldDataBeforeUpload) {
                        repository.clearAll()
                    }
                    repository.insertAll(records)
                }
                _importingState.value = ImportingState.Success(records.size)
            } else {
                _importingState.value = ImportingState.Error("No valid movie records found. Ensure columns match B, D, E, and F.")
            }
        }
    }

    fun syncFromZoho(context: Context) {
        viewModelScope.launch {
            _importingState.value = ImportingState.Loading
            try {
                val prefs = ZohoPreferences(context)
                val newRecords = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    ZohoSyncManager.performSync(
                        accountsServer = prefs.accountsServer,
                        apiServer = prefs.apiServer,
                        clientId = prefs.clientId,
                        clientSecret = prefs.clientSecret,
                        refreshToken = prefs.refreshToken,
                        folderId = prefs.folderId,
                        configFileName = prefs.fileName,
                        configExtension = prefs.defaultExtension
                    )
                }

                if (newRecords.isNotEmpty()) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        if (prefs.clearOldDataBeforeUpload) {
                            repository.clearAll()
                        }
                        repository.insertAll(newRecords)
                    }
                    _importingState.value = ImportingState.Success(newRecords.size)
                } else {
                    _importingState.value = ImportingState.Error("Worksheet parsed, but returned 0 valid records.")
                }
            } catch (e: Exception) {
                Log.e("MovieViewModel", "Zoho Sheets sync failed", e)
                _importingState.value = ImportingState.Error(e.message ?: "An unknown Zoho sync error occurred.")
            }
        }
    }

    fun addManualMovie(name: String, sublink: String, category: String, link: String, pageUrl: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(
                MovieRecord(
                    name = name,
                    sublink = sublink,
                    category = category,
                    link = link,
                    pageUrl = pageUrl
                )
            )
            onComplete()
        }
    }

    fun deleteMovie(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }
}

class MovieViewModelFactory(private val repository: MovieRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovieViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MovieViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
