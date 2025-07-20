package mm.com.wavemoney.fullopencvtesting

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class LivePhotoViewModel (): ViewModel(){
    private val _faceUIState = MutableLiveData<FaceUIState>()
    val faceUIState: LiveData<FaceUIState> get() = _faceUIState

    private val _isPreviewMode = MutableLiveData<Boolean>()
    val isPreviewMode: LiveData<Boolean> get() = _isPreviewMode

    private val _isNeedStopFaceDetection = MutableLiveData<CaptureBtnState>()
    val isNeedStopFaceDetection: LiveData<CaptureBtnState> get() = _isNeedStopFaceDetection

    fun updatePreviewState(isInPreviewVisiable: Boolean) {
        _isPreviewMode.postValue(isInPreviewVisiable)
    }
    fun updateDetectionObservation(state:CaptureBtnState) {
        viewModelScope.launch {
            _isNeedStopFaceDetection.postValue(state)
        }
    }

    fun updateFaceDetectionState(
        isInitialState: Boolean,
        detectionResult: String,
        isMultipleFaces: Boolean,
        isDetectionReady: Boolean,
    ) {
        val state = when {
            isInitialState -> FaceUIState.Initial(detectionResult)
            isMultipleFaces -> FaceUIState.DetectingMultipleFaces(detectionResult)
            isDetectionReady -> FaceUIState.ReadyToCapture(detectionResult)
            else -> FaceUIState.DetectingSingleButNotReady(detectionResult)
        }
        _faceUIState.postValue(state)
    }


}
data class CaptureBtnState(
    val isNeedToStop: Boolean,
    val bitmap: Bitmap?
)
sealed class FaceUIState {
    data class Initial(val message: String) : FaceUIState()
    data class DetectingMultipleFaces(val message: String) : FaceUIState()
    data class ReadyToCapture(val message: String) : FaceUIState()
    data class DetectingSingleButNotReady(val message: String) : FaceUIState()
}