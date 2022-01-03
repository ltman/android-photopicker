package com.ltman.photopicker_sample

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.fondesa.kpermissions.allGranted
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.ltman.photopicker_core.media.loader.DefaultMediaFileLoader
import com.ltman.photopicker_core.media.loader.IMediaFileLoader
import com.ltman.photopicker_core.media.model.MediaFile
import com.ltman.photopicker_sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), IMediaFileLoader.Listener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            val request = permissionsBuilder(Manifest.permission.READ_EXTERNAL_STORAGE).build()
            request.addListener { result ->
                if (result.allGranted()) {
                    // All the permissions are granted.
                    val mediaLoader = DefaultMediaFileLoader()
                    mediaLoader.loadDeviceMediaFiles(
                        this,
                        IMediaFileLoader.Config(
                            isIncludeVideo = true
                        ),
                        this
                    )
                }
            }

            request.send()

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onMediaFileLoaded(mediaFiles: List<MediaFile>) {
        Log.d("Pong", "onMediaFileLoaded ${mediaFiles.map { it.path }}")
    }

    override fun onMediaFileFailed(error: Throwable) {
        Log.d("Pong", "onMediaFileFailed $error")
    }
}