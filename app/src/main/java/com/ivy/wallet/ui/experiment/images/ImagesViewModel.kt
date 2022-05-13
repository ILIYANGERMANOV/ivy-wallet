package com.ivy.wallet.ui.experiment.images

import com.ivy.fp.action.then
import com.ivy.fp.monad.Res
import com.ivy.fp.viewmodel.IvyViewModel
import com.ivy.wallet.domain.action.viewmodel.experiment.FetchImagesAct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class ImagesViewModel @Inject constructor(
    private val fetchImagesAct: FetchImagesAct
) : IvyViewModel<ImagesState, ImagesEvent>() {
    override val _state: MutableStateFlow<ImagesState> =
        MutableStateFlow(ImagesState.Loading)

    override suspend fun handleEvent(event: ImagesEvent): suspend () -> ImagesState = when (event) {
        is ImagesEvent.Load -> loadImages()
    }

    private suspend fun loadImages() = suspend {
        updateState { ImagesState.Loading }
    } then fetchImagesAct then {
        when (it) {
            is Res.Err<Exception, *> -> ImagesState.Error(it.error.message ?: "idk")
            is Res.Ok<*, List<String>> -> ImagesState.Success(it.data)
        }
    }
}