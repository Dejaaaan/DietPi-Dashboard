package com.example

import android.app.Application
import android.system.Os
import android.util.Log

class DietPiApplication : Application() {

    init {
        configureSoftwareRendering()
    }

    override fun onCreate() {
        super.onCreate()
        configureSoftwareRendering()
    }

    private fun configureSoftwareRendering() {
        try {
            // Force Mesa to use software rasterizer (llvmpipe/swrast) and avoid probing DRM /dev/dri/renderD128
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("GALLIUM_DRIVER", "llvmpipe", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
            Os.setenv("MESA_DEBUG", "0", true)
        } catch (e: Throwable) {
            Log.d("DietPiApplication", "Unable to configure software rendering: ${e.message}")
        }
    }
}
