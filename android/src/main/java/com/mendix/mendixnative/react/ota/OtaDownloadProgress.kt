package com.mendix.mendixnative.react.ota

import android.app.Activity

/**
 * Receives OTA download progress on the UI thread, e.g. to render it on the splash screen.
 */
interface OtaDownloadProgressListener {
  /** [totalBytes] is -1 when the server does not send a Content-Length. */
  fun onOtaDownloadProgress(activity: Activity, receivedBytes: Long, totalBytes: Long)

  /** Called when the download succeeded or failed. */
  fun onOtaDownloadFinished()
}

object OtaDownloadProgress {
  @JvmStatic
  var listener: OtaDownloadProgressListener? = null
}
