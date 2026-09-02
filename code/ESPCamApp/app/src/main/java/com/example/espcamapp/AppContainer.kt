package com.example.espcamapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.espcamapp.data.remote.Http
import com.example.espcamapp.data.repo.AccessRepository
import com.example.espcamapp.data.repo.DetectionRepository
import com.example.espcamapp.data.repo.DeviceRepository
import com.example.espcamapp.data.repo.PeopleRepository
import com.example.espcamapp.data.settings.SettingsStore

/**
 * Injeção de dependências manual: o projeto tem poucas dependências e um
 * container simples evita o peso do Hilt/KSP num app deste tamanho.
 */
class AppContainer(context: Context) {

    val settings = SettingsStore(context.applicationContext)

    private val esp32Api = Http.esp32Api()
    private val faceServerApi = Http.faceServerApi()

    // Cadastro de amostras e reconhecimento passam pelo cliente de timeout longo.
    private val faceServerHeavyApi = Http.faceServerHeavyApi()

    val accessRepository = AccessRepository(faceServerApi, faceServerHeavyApi, settings)
    val deviceRepository = DeviceRepository(esp32Api, settings)
    val peopleRepository = PeopleRepository(faceServerApi, faceServerHeavyApi, settings)
    val detectionRepository = DetectionRepository(faceServerApi, faceServerHeavyApi, settings)
}

class EspCamApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Atalho para pegar o container dentro de uma factory de `ViewModel`. */
val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EspCamApplication).container
