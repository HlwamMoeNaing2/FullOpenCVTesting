package mm.com.wavemoney.fullopencvtesting.models

/**
 * This class contains the original document photo, and a cropper. The user can drag the corners
 * to make adjustments to the detected corners.
 *

 * @param corners the document's 4 corner points
 * @constructor creates a document
 */
class Document(
    var corners: Quad
) {
}