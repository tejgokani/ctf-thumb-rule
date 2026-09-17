package com.tejgokani.strata

import android.app.Application
import com.tejgokani.strata.di.AppContainer

class StrataApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
