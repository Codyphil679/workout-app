package com.codyphil.liftlog.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codyphil.liftlog.AppContainer
import com.codyphil.liftlog.data.ExerciseSetRow
import com.codyphil.liftlog.data.SessionDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveWorkout : WorkoutUiState
    data class Active(val detail: SessionDetail) : WorkoutUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModel(container: AppContainer) : ViewModel() {
    val repo = container.repository
    val settings = container.settings.settings

    private val _lastTime = MutableStateFlow<Map<Long, List<ExerciseSetRow>>>(emptyMap())
    /** exerciseId -> sets from the last finished workout containing that exercise. */
    val lastTime: StateFlow<Map<Long, List<ExerciseSetRow>>> = _lastTime.asStateFlow()

    val state: StateFlow<WorkoutUiState> = repo.observeActiveSession()
        .flatMapLatest { s -> if (s == null) flowOf(null) else repo.observeSessionDetail(s.id) }
        .onEach { detail -> detail?.exercises?.forEach { loadLastTime(it.row.exerciseId) } }
        .map { if (it == null) WorkoutUiState.NoActiveWorkout else WorkoutUiState.Active(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

    private fun loadLastTime(exerciseId: Long) {
        if (_lastTime.value.containsKey(exerciseId)) return
        _lastTime.value += exerciseId to emptyList()
        viewModelScope.launch { _lastTime.value += exerciseId to repo.lastTimeSets(exerciseId) }
    }

    fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
