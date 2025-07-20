package mm.com.wavemoney.fullopencvtesting

// import org.opencv.android.OpenCVLoader
// import org.opencv.core.Mat
// import org.opencv.core.Point
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import mm.com.wavemoney.fullopencvtesting.databinding.FragmentCardDetectionBinding
import mm.com.wavemoney.fullopencvtesting.document_scanner.FrameAnalyzer
import mm.com.wavemoney.fullopencvtesting.models.Quad
import mm.com.wavemoney.fullopencvtesting.utils.ImageUtil
import mm.com.wavemoney.fullopencvtesting.utils.setSystemNavigationBarColor
import mm.com.wavemoney.fullopencvtesting.utils.updateStatusBarColor
import org.opencv.core.Mat
import org.opencv.core.Point
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest
import java.util.concurrent.Executors


class CardDetectionFragment : Fragment() , EasyPermissions.PermissionCallbacks{

    private var _binding: FragmentCardDetectionBinding? = null
    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!



    private var capturedCount = 0
    private val imageUtils = ImageUtil()
    private var isCardDetected: Boolean = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var currentDetectedCorners: List<Point>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var latestCroppedBitmap: Bitmap? = null
    private var latestDetectedCorners: List<Point>? = null

    private val requestCodeCameraPermission = 1001

    private var camera: Camera? = null
    private var isChooseFromGallery = false


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCardDetectionBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateStatusBarColor(Color.BLACK)
        setSystemNavigationBarColor(R.color.black)
        // OpenCVLoader.initLocal() // Removed OpenCV dependency
        currentDetectedCorners = null
        latestCroppedBitmap = null
        latestDetectedCorners = null
        setupViews()
        setupEvents()
        toggleCaptureAndSubmitContainer(null, false)

        //common app bar default background color is wave yellow color. initially change it to black color
        val appBarBackground =
            ContextCompat.getColor(requireContext(), R.color.black)
        val appBar = binding.photoIdAppBar.commonAppBarMain
        appBar.setBackgroundColor(appBarBackground)
    }
    private fun setupEvents() {

        binding.btnCapture.setOnClickListener {
            isChooseFromGallery = false
            val isPolygonDrawn = binding.polygonOverlay.isPolygonDrawn()

            val bitmapToUse = if (isPolygonDrawn) {
                latestCroppedBitmap ?: binding.previewView.bitmap ?: return@setOnClickListener
            } else {
                binding.previewView.bitmap ?: return@setOnClickListener
            }
            currentDetectedCorners = latestDetectedCorners
            toggleCaptureAndSubmitContainer(bitmapToUse, true)
        }


        binding.btnRetake.setOnClickListener {
            isCardDetected = false
            currentDetectedCorners = null
            latestCroppedBitmap = null
            latestDetectedCorners = null
            binding.polygonOverlay.visibility = View.VISIBLE
            toggleCaptureAndSubmitContainer(null, false)
            isChooseFromGallery = false
        }

        binding.btnSubmit.setOnClickListener {
            capturedCount++
            binding.ivPreview.drawable?.let { drawable ->
                drawable.toBitmap().let {
                   // viewModel.saveBitmap(it, poiImageTypeToCapture, capturedCount)
                }
            }
        }

    }
    private fun setupViews() {
        isCardDetected = false
        if (hasCameraPermission) {
            binding.previewView.startCamera { camera = it }
        } else {
            requestCameraPermission()
        }
    }

    private fun requestCameraPermission() {
        EasyPermissions.requestPermissions(
            PermissionRequest.Builder(this, requestCodeCameraPermission, Manifest.permission.CAMERA)
                .setRationale("We need to access your camera to scan documents.")
                .setPositiveButtonText(R.string.ok)
                .setNegativeButtonText(R.string.cancel)
                .build()
        )
    }
    private fun PreviewView.startCamera(onBind: (Camera) -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                setSurfaceProvider(surfaceProvider)
            }

            // 2) ImageAnalysis use-case: run SimpleFrameAnalyzer on each frame
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        FrameAnalyzer(
                            previewView = binding.previewView,
                            onDocumentDetected = { rotatedBitmap, orderedCorners, mappedPoints ->
                                /*
                                for auto capture
                                min width should -> 290
                                min height should -> 300
                                And then check camera stability,
                                Then - capture and get bitmap
                                and then follow the processAndDisplayCroppedImage logic
                                 */
                                // Crop using the PreviewView coordinates

//                                private fun cropFromPreviewViewCoordinates(rotatedMat: Mat, mappedPoints: List<Point>)
                                val croppedBitmap =
                                    cropFromPreviewViewCoordinates(rotatedMat = rotatedBitmap,mappedPoints = mappedPoints)
                                latestCroppedBitmap = croppedBitmap
                            },
                            onPolygonDetected = { mappedPoints ->
                                // Always run on UI thread:
                                val mainHandler = Handler(Looper.getMainLooper())
                                mainHandler.post {
                                    if (isAdded && view != null) {
                                        binding.polygonOverlay.setPoints(mappedPoints)
                                    }
                                }

                                // Store the latest detected corners
                                latestDetectedCorners = mappedPoints

                                // concern block
                                // Update card detection state and UI
                                val cardDetected = mappedPoints.isNotEmpty()
                                if (cardDetected != isCardDetected) {
                                    isCardDetected = cardDetected
                                    // updateCardDetectionUI(cardDetected)
                                    //TODO update UI with view model of directly
                                    // TODO , stop frame analyzer's   printing log and it's job   Log.d("FrameAnalyzer", "🔍 Quad found: area=$area")
                                }
                            })
                    )
                }


            val imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                val camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalysis
                )

                onBind(camera)
            } catch (exc: Exception) {

            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun ImageView.setImage(@DrawableRes id: Int) {
        setImageDrawable(ContextCompat.getDrawable(requireContext(), id))
    }
    override fun onResume() {
        super.onResume()
        capturedCount = 0
        restartImageAnalysis()
    }
    override fun onPause() {
        super.onPause()
        stopFrameAnalysis()
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        stopFrameAnalysis()
    }

    private val hasCameraPermission
        get() = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (requestCode == requestCodeCameraPermission && perms.contains(Manifest.permission.CAMERA)) {
            binding.previewView.startCamera { camera = it }
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }
    private fun toggleCaptureAndSubmitContainer(imageBm: Bitmap?, isFinishedCaptured: Boolean) {
        if (isFinishedCaptured) {
            binding.apply {
                captureGuideContainer.visibility = View.GONE
                previewView.visibility = View.GONE
                ivPreview.visibility = View.VISIBLE
                //  ivPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                ivPreview.setImageBitmap(imageBm)
                binding.polygonOverlay.visibility = View.GONE

                lySubmitContainer.visibility = View.VISIBLE
            }

        } else {
            binding.apply {
                clearCapturedImage()
                captureGuideContainer.visibility = View.VISIBLE
                lySubmitContainer.visibility = View.GONE
                previewView.visibility = View.VISIBLE
                ivPreview.visibility = View.GONE
                ivPreview.setImageBitmap(imageBm)
                binding.polygonOverlay.visibility = View.VISIBLE
            }
        }
    }

    private fun clearCapturedImage() {
        binding.apply {
            polygonOverlay.setPoints(emptyList())
        }
    }
    private fun stopFrameAnalysis() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null

    }

    fun restartImageAnalysis() {
        if (hasCameraPermission) {
            if (imageAnalysis == null) {
                binding.previewView.startCamera { camera = it }
            }
        } else {
            requestCameraPermission()
        }
    }

    private fun cropFromPreviewViewCoordinates(rotatedMat: Mat, mappedPoints: List<Point>): Bitmap {
        if (mappedPoints.size != 4) {
            // Fallback to original mat if no valid points
            return imageUtils.matToBitmap(rotatedMat)
        }

        // Convert PreviewView coordinates back to Mat coordinates
        val matPoints = previewViewPointsToMatPoints(mappedPoints, rotatedMat, binding.previewView)

        // Create a Quad from the mat points
        val quad = Quad(
            matPoints[0],  // topLeft
            matPoints[1],  // topRight
            matPoints[2],  // bottomRight
            matPoints[3]   // bottomLeft
        )

        // Use the existing ImageUtil.crop method
        val croppedBitmap = imageUtils.crop(imageUtils.matToBitmap(rotatedMat), quad)
        return croppedBitmap
    }

    private fun previewViewPointsToMatPoints(
        viewPoints: List<Point>,
        rotatedMat: Mat,
        previewView: PreviewView
    ): List<Point> {
        // Reverse the mapping from matPointsToViewPoints
        val matWidth = rotatedMat.width().toFloat()
        val matHeight = rotatedMat.height().toFloat()
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        val scaleX = viewWidth / matWidth
        val scaleY = viewHeight / matHeight
        val scale = maxOf(scaleX, scaleY)

        val scaledMatWidth = matWidth * scale
        val scaledMatHeight = matHeight * scale
        val dx = (scaledMatWidth - viewWidth) / 2f
        val dy = (scaledMatHeight - viewHeight) / 2f

        return viewPoints.map { pt ->
            val mx = (pt.x + dx) / scale
            val my = (pt.y + dy) / scale
            Point(mx.toDouble(), my.toDouble())
        }
    }

}