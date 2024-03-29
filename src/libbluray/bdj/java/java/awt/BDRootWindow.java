/*
 * This file is part of libbluray
 * Copyright (C) 2012  Libbluray
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package java.awt;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import org.videolan.Logger;
import org.videolan.Libbluray;

public class BDRootWindow extends Frame {

    public BDRootWindow () {
        super();
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        BDToolkit.setFocusedWindow(this);
    }

    public Area getDirtyArea() {
        return dirty;
    }

    public void setBounds(int x, int y, int width, int height) {
        if (!isVisible()) {
            if ((width > 0) && (height > 0)) {
                if ((backBuffer == null) || (getWidth() * getHeight() < width * height)) {
                    backBuffer = new int[width * height];
                    Arrays.fill(backBuffer, 0);
                }
            }
            super.setBounds(x, y, width, height);
        } else {
            logger.error("setBounds(" + x + "," + y + "," + width + "," + height + ") FAILED: already visible");
        }
    }

    public int[] getBdBackBuffer() {
        return backBuffer;
    }

    public Image getBackBuffer() {
        /* exists only in J2SE */
        logger.unimplemented("getBackBuffer");
        return null;
    }

    public void notifyChanged() {
        if (!isVisible()) {
            logger.error("sync(): not visible");
            return;
        }
        synchronized (this) {
            if (timer == null) {
                logger.error("notifyChanged(): window already disposed");
                return;
            }
            changeCount++;
            if (timerTask == null) {
                timerTask = new RefreshTimerTask(this);
                timer.schedule(timerTask, 40, 40);
            }
        }
    }

    public void sync() {
        synchronized (this) {
            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            changeCount = 0;

            Area a = dirty.getBounds();
            dirty.clear();

            if (!a.isEmpty()) {
                if (!overlay_open) {
                    Libbluray.updateGraphic(getWidth(), getHeight(), null);
                    overlay_open = true;
                    a = new Area(getWidth(), getHeight()); /* force full plane update */
                }
                Libbluray.updateGraphic(getWidth(), getHeight(), backBuffer, a.x0, a.y0, a.x1, a.y1);
            }
        }
    }

    private class RefreshTimerTask extends TimerTask {
        public RefreshTimerTask(BDRootWindow window) {
            this.window = window;
            this.changeCount = window.changeCount;
        }

        public void run() {
            synchronized (window) {
                if (this.changeCount == window.changeCount)
                    window.sync();
                else
                    this.changeCount = window.changeCount;
            }
        }

        private BDRootWindow window;
        private int changeCount;
    }

    private void close() {
        synchronized (this) {
            if (overlay_open) {
                Libbluray.updateGraphic(0, 0, null);
                overlay_open = false;
            }
        }
    }

    public void setVisible(boolean visible) {

        super.setVisible(visible);

        if (!visible) {
            close();
        }
    }

    /* called when new title starts (window is "created" again) */
    public void clearOverlay() {
        synchronized (this) {
            if (overlay_open) {
                logger.error("clearOverlay() ignored (overlay is visible)");
            } else {
                Arrays.fill(backBuffer, 0);
                dirty.clear();
            }
        }
    }

    public void dispose()
    {
        synchronized (this) {
            if (timerTask != null) {
                timerTask.cancel();
                timerTask = null;
            }
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }

        if (isVisible()) {
            hide();
        }

        BDToolkit.setFocusedWindow(null);

        super.dispose();

        backBuffer = null;
    }

    private int[] backBuffer = null;
    private Area dirty = new Area();
    private int changeCount = 0;
    private Timer timer = new Timer();
    private TimerTask timerTask = null;
    private boolean overlay_open = false;

    private static final Logger logger = Logger.getLogger(BDRootWindow.class.getName());

    private static final long serialVersionUID = -8325961861529007953L;
}
