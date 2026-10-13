package com.aidriy.app.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.aidriy.app.AidriyApplication

/**
 * Gives ViewModel factories access to the app's dependency container:
 *
 * ```
 * val Factory = viewModelFactory {
 *     initializer { MyViewModel(appContainer().someRepository) }
 * }
 * ```
 */
fun CreationExtras.appContainer(): AppContainer {
    val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "Application is missing from CreationExtras"
    }
    return (application as AidriyApplication).container
}
