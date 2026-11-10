package ar.nogoya.pronostico

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.nogoya.pronostico.ui.PantallaPrincipal
import ar.nogoya.pronostico.ui.PronosticoTheme
import ar.nogoya.pronostico.ui.PronosticoViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PronosticoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PronosticoTheme {
                PantallaPrincipal(viewModel)
            }
        }
    }

    /** Cada vez que la app pasa a primer plano (incluida la apertura desde la notificación) se actualiza. */
    override fun onStart() {
        super.onStart()
        viewModel.actualizar()
    }
}
