package ar.nogoya.pronostico.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.nogoya.pronostico.BuildConfig
import ar.nogoya.pronostico.datos.AlmacenPrefs
import ar.nogoya.pronostico.datos.Carga
import ar.nogoya.pronostico.datos.FuenteHttp
import ar.nogoya.pronostico.datos.Repositorio
import ar.nogoya.pronostico.datos.ResultadoLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiEstado(
    val cargando: Boolean = true,
    val resultado: ResultadoLatest? = null,
    val aviso: String? = null,
    val error: String? = null,
)

class PronosticoViewModel(application: Application) : AndroidViewModel(application) {

    private val repositorio = Repositorio(
        fuente = FuenteHttp(BuildConfig.URL_LATEST),
        almacen = AlmacenPrefs(application),
    )

    private val _estado = MutableStateFlow(UiEstado())
    val estado: StateFlow<UiEstado> = _estado.asStateFlow()

    private var trabajo: Job? = null

    init {
        actualizar()
    }

    fun actualizar() {
        if (trabajo?.isActive == true) return
        _estado.update { it.copy(cargando = true) }
        trabajo = viewModelScope.launch {
            val carga: Carga = try {
                withContext(Dispatchers.IO) { repositorio.cargar() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Carga.Fallo("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
            }
            _estado.update { actual ->
                when (carga) {
                    is Carga.Exito -> UiEstado(
                        cargando = false,
                        resultado = carga.resultado,
                        aviso = carga.aviso,
                    )
                    // Se conserva el último resultado de esta sesión; la vigencia la decide la pantalla.
                    is Carga.Fallo -> actual.copy(cargando = false, aviso = null, error = carga.mensaje)
                }
            }
        }
    }
}
