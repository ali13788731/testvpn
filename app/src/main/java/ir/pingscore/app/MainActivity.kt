package ir.pingscore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.pingscore.app.ui.AppRoot
import ir.pingscore.app.ui.PingScoreTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PingScoreTheme {
                val viewModel: MainViewModel = viewModel()
                AppRoot(viewModel)
            }
        }
    }
}
