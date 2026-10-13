package com.mendix.mendixnative.react.download

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.*
import java.net.ConnectException

@Throws(
  IllegalArgumentException::class,
  ConnectException::class,
  FileAlreadyExistsException::class,
  NoDataException::class,
  FileCorruptionException::class,
  IOException::class,
  SecurityException::class,
  ConnectException::class,
  DownloadMimeTypeException::class
)

fun downloadFile(
  client: OkHttpClient,
  url: String,
  downloadPath: String,
  onSuccess: () -> Unit,
  onFailure: (e: Exception) -> Unit,
  progressCallback: (receivedBytes: Double, totalBytes: Double) -> Unit = { _, _ -> },
) {
  downloadFile(client, url, downloadPath, null, onSuccess, onFailure, progressCallback)
}

fun downloadFile(
  client: OkHttpClient,
  url: String,
  downloadPath: String,
  expectedMimeType: String?,
  onSuccess: () -> Unit,
  onFailure: (e: Exception) -> Unit,
  progressCallback: (receivedBytes: Double, totalBytes: Double) -> Unit = { _, _ -> },
) {
  val outputFile = File(downloadPath)
  if (outputFile.exists()) throw FileAlreadyExistsException(outputFile)
  outputFile.parentFile?.mkdirs()
  outputFile.createNewFile()

  try {
    client.newCall(Request.Builder().url(url).get().build()).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        outputFile.delete()
        onFailure(e)
      }

      override fun onResponse(call: Call, response: Response) {
        try {
          DownloadResponseHandler(
            response,
            expectedMimeType,
            outputFile,
            progressCallback,
          ).handle()
          onSuccess()
        } catch (e: Exception) {
          onFailure(e)
        }
      }
    })
  } catch (e: Exception) {
    outputFile.delete()
    throw e
  }
}


fun makeProgressCallbackInvoker(
  bytesInterval: Double,
  cb: (receivedBytes: Double, totalBytes: Double) -> Unit
): (Double, Double) -> Unit {
  var invokeNext = bytesInterval
  return fun(receivedBytes: Double, totalBytes: Double) {
    if (receivedBytes >= invokeNext) {
      invokeNext = receivedBytes + bytesInterval
      cb.invoke(receivedBytes, totalBytes)
    }
  }
}

class DownloadResponseHandler(
  private val response: Response,
  private val expectedMimeType: String?,
  private val outputFile: File,
  private val progressCallback: (receivedBytes: Double, totalBytes: Double) -> Unit = { _, _ -> },
) {
  @Throws(ConnectException::class, NoDataException::class, DownloadMimeTypeException::class)
  fun handle() {
    var inputStream: BufferedInputStream? = null
    var outputStream: BufferedOutputStream? = null
    try {
      if (!response.isSuccessful) throw ConnectException()
      if (response.body == null) throw NoDataException()
      val body = response.body
      val mediaType = body?.contentType()
      if (expectedMimeType != null && mediaType != expectedMimeType
          .toMediaTypeOrNull()
      ) throw DownloadMimeTypeException()


      inputStream = BufferedInputStream(body!!.byteStream())

      outputStream =
        BufferedOutputStream(FileOutputStream(outputFile))

      val totalBytes = response.body!!.contentLength().toDouble()
      // Report every 1%, or every 64 KB when the server sends no Content-Length (totalBytes = -1)
      val progressCallbackInvoker = makeProgressCallbackInvoker(
        if (totalBytes > 0) totalBytes / 100 else 64.0 * 1024,
        progressCallback
      )

      val buffer = ByteArray(8 * 1024)
      var receivedBytes = 0.0
      var read = inputStream.read(buffer)
      while (read != -1) {
        outputStream.write(buffer, 0, read)
        receivedBytes += read
        progressCallbackInvoker(receivedBytes, totalBytes)
        read = inputStream.read(buffer)
      }
      outputStream.flush()
    } catch (e: Exception) {
      outputFile.delete()
      throw e
    } finally {
      outputStream?.close()
      inputStream?.close()
    }
  }
}

class NoDataException : IllegalStateException()
class FileCorruptionException : IllegalStateException()
class DownloadMimeTypeException : RuntimeException()
