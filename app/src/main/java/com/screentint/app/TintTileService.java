package com.screentint.app;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick settings tile (swipe down from the top) that turns the tint on and off. */
public class TintTileService extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        Tint.setEnabled(this, !Tint.enabled(this));
        update();
    }

    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tint.enabled(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
