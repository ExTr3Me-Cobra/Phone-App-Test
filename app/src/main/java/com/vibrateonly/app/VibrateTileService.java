package com.vibrateonly.app;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick settings tile (swipe down from the top) that toggles the mode. */
public class VibrateTileService extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        ModeController.toggle(this);
        update();
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(ModeController.isActive(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
