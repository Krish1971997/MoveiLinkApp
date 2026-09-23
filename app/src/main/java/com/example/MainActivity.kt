package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MovieDatabase
import com.example.repository.MovieRepository
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MovieViewModel
import com.example.viewmodel.MovieViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Instantiate database, DAO, and repository
        val database = MovieDatabase.getDatabase(applicationContext)
        val repository = MovieRepository(database.movieDao())

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Compose-native ViewModel injection
                    val viewModel: MovieViewModel = viewModel(
                        factory = MovieViewModelFactory(repository)
                    )

                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
