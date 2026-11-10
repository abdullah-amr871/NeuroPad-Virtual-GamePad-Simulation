package io.github.abdullahamr871.neuropad.bridge

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

/** Quick Settings tile: start or stop the bridge without leaving the game. */
@RequiresApi(Build.VERSION_CODES.N)
class BridgeTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (BridgeState.status.value.isActive) {
            GamepadBridgeService.stop(this)
        } else {
            GamepadBridgeService.start(this)
        }
        // The service updates BridgeState asynchronously; show the intent immediately.
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (BridgeState.status.value.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
