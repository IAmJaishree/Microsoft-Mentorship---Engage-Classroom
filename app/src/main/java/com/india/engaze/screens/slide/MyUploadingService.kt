package com.india.engaze.screens.slide

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.india.engaze.R
import com.india.engaze.screens.Splash.SplashActivity
import com.india.engaze.utils.MyBaseTaskService

class MyUploadingService : MyBaseTaskService() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /*
    0 -> slide
    1 -> assignment
     */

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand:$intent:$startId")
        if (ACTION_UPLOAD == intent.action) {
            val fileUri = intent.getParcelableExtra<Uri>("fileUri")
//            val classId = intent.getStringExtra("classId")
//            val userId = intent.getStringExtra(("userId"))
            val json = intent.getStringExtra("data")
            val storagePath = intent.getStringExtra("storagePath")
            val databasePath = intent.getStringExtra("databasePath")
            val link = intent.getStringExtra("link")?:"link"

            val gson = GsonBuilder().setPrettyPrinting().create()
            val data: HashMap<String, Any> = gson.fromJson(json, object : TypeToken<HashMap<String, Any>>() {}.type)

//             Make sure we have permission to read the data
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                contentResolver.takePersistableUriPermission(
//                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION//)
//            }

            uploadFromUri(fileUri, storagePath, databasePath, data, link)

        }

        return Service.START_REDELIVER_INTENT
    }

    private fun uploadFromUri(fileUri: Uri, storagePath: String, databasePath: String, data:HashMap<String,Any>, link:String) {
        Log.d(TAG, "uploadFromUri:src:" + fileUri.toString())
        Log.d(TAG, "Storage Path : $storagePath")
        Log.d(TAG, "Database Path : $databasePath")
        Log.d(TAG, "Database Link : $link")

        taskStarted()
        showProgressNotification(getString(R.string.progress_uploading), 0, 0, R.drawable.ic_cloud_upload_white_24dp)
        val fileName = fileUri.lastPathSegment

        val fileRef = FirebaseStorage.getInstance().getReference(storagePath)

        fileRef.putFile(fileUri).addOnProgressListener { taskSnapshot ->
            showProgressNotification(getString(R.string.progress_uploading),
                    taskSnapshot.bytesTransferred,
                    taskSnapshot.totalByteCount, R.drawable.ic_cloud_upload_white_24dp)
        }.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception!!
            }

            Log.d(TAG, "uploadFromUri: upload success")

            // Request the public download URL
            fileRef.downloadUrl
        }.addOnSuccessListener { downloadUri ->
            Log.d(TAG, "uploadFromUri: getDownloadUri success")
            data[link] = downloadUri.toString()
            updateDatabase(fileName!!, databasePath,data)
//            broadcastUploadFinished(downloadUri, fileUri)
            showUploadFinishedNotification(downloadUri, fileUri)
            taskCompleted()
        }.addOnFailureListener { exception ->
            Log.w(TAG, "uploadFromUri:onFailure", exception)
//            broadcastUploadFinished(null, fileUri)
            showUploadFinishedNotification(null, fileUri)
            taskCompleted()
        }
    }

    private fun updateDatabase(fileName: String, databasePath: String,data:HashMap<String,Any>) {

//        val map = HashMap<String, String>()
//        map["title"] = fileName
//        map["link"] = downloadUri.toString()

        val databaseReference = FirebaseDatabase.getInstance().getReference(databasePath)
        databaseReference.updateChildren(data).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("Engaze", "Successfully uploaded")
                //              Toast.makeText(this, "Successfully uploaded", Toast.LENGTH_LONG).show()
            } else {
                Log.d("Engaze", "failure listener mRootRef")
                //            Toast.makeText(this, "Error", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show a notification for a finished upload.
     */
    private fun showUploadFinishedNotification(downloadUrl: Uri?, fileUri: Uri?) {
        dismissProgressNotification()

        val intent = Intent(this, SplashActivity::class.java)
                .putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                .putExtra(EXTRA_FILE_URI, fileUri)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val success = downloadUrl != null
        val caption = if (success) getString(R.string.upload_success) else getString(R.string.upload_failure)
        showFinishedNotification(caption, intent, success)
    }

    companion object {

        private const val TAG = "MyUploadService"

        /** Intent Actions  */
        const val ACTION_UPLOAD = "action_upload"
        const val UPLOAD_COMPLETED = "upload_completed"
        const val UPLOAD_ERROR = "upload_error"

        /** Intent Extras  */
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_DOWNLOAD_URL = "extra_download_url"

        val intentFilter: IntentFilter
            get() {
                val filter = IntentFilter()
                filter.addAction(UPLOAD_COMPLETED)
                filter.addAction(UPLOAD_ERROR)

                return filter
            }
    }

}