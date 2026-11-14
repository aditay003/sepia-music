package app.sepia.music

import android.app.Application

class SepiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
