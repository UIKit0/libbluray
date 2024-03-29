/*
 * This file is part of libbluray
 * Copyright (C) 2010      William Hahne
 * Copyright (C) 2012-2014 Petri Hintukainen <phintuka@users.sourceforge.net>
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

package org.videolan.media.content.playlist;

import java.io.IOException;

import javax.media.ClockStartedError;
import javax.media.ConnectionErrorEvent;
import javax.media.Control;
import javax.media.ControllerErrorEvent;
import javax.media.IncompatibleSourceException;
import javax.media.Time;
import javax.media.protocol.DataSource;
import javax.tv.locator.InvalidLocatorException;

import org.bluray.media.InvalidPlayListException;
import org.bluray.net.BDLocator;
import org.bluray.system.RegisterAccess;
import org.videolan.BDJAction;
import org.videolan.BDJActionManager;
import org.videolan.Libbluray;
import org.videolan.PlaylistInfo;
import org.videolan.TIClip;
import org.videolan.media.content.BDHandler;

public class Handler extends BDHandler {
    public Handler() {
        controls = new Control[18];
        controls[0] = new AngleControlImpl(this);
        controls[1] = new AudioMetadataControlImpl();
        controls[2] = new BackgroundVideoPresentationControlImpl(this);
        controls[3] = new DVBMediaSelectControlImpl(this);
        controls[4] = new MediaTimeEventControlImpl();
        controls[5] = new MediaTimePositionControlImpl(this);
        controls[6] = new OverallGainControlImpl();
        controls[7] = new PanningControlImpl();
        controls[8] = new PiPControlImpl(this);
        controls[9] = new PlaybackControlImpl(this);
        controls[10] = new PlayListChangeControlImpl(this);
        controls[11] = new PrimaryAudioControlImpl(this);
        controls[12] = new PrimaryGainControlImpl();
        controls[13] = new SecondaryAudioControlImpl(this);
        controls[14] = new SecondaryGainControlImpl();
        controls[15] = new SubtitlingControlImpl(this);
        controls[16] = new UOMaskTableControlImpl(this);
        controls[17] = new VideoFormatControlImpl(this);
    }

    public void setSource(DataSource source) throws IOException, IncompatibleSourceException {
        synchronized (this) {
            try {
                locator = new BDLocator(source.getLocator().toExternalForm());
            } catch (org.davic.net.InvalidLocatorException e) {
                throw new IncompatibleSourceException();
            }
            if (!locator.isPlayListItem())
                throw new IncompatibleSourceException();
            pi = Libbluray.getPlaylistInfo(locator.getPlayListId());
            if (pi == null)
                throw new IOException();
            baseMediaTime = 0;
            if (state == Prefetched)
                doPrefetch();
        }
    }

    public Time getDuration() {
        long duration = pi.getDuration() ;
        return new Time(duration * TO_SECONDS);
    }

    protected ControllerErrorEvent doPrefetch() {
        synchronized (this) {
            try {
                int stream;
                stream = locator.getPrimaryAudioStreamNumber();
                if (stream > 0)
                    Libbluray.writePSR(Libbluray.PSR_PRIMARY_AUDIO_ID, stream);
                stream = locator.getPGTextStreamNumber();
                if (stream > 0) {
                    Libbluray.writePSR(Libbluray.PSR_PG_STREAM, stream, 0x00000fff);
                }
                stream = locator.getSecondaryVideoStreamNumber();
                if (stream > 0) {
                    Libbluray.writePSR(Libbluray.PSR_SECONDARY_AUDIO_VIDEO, stream << 8, 0x0000ff00);
                }
                stream = locator.getSecondaryAudioStreamNumber();
                if (stream > 0) {
                    Libbluray.writePSR(Libbluray.PSR_SECONDARY_AUDIO_VIDEO, stream, 0x000000ff);
                }

                int pl = locator.getPlayListId();
                long time = -1;
                int pi = -1, mark = -1;
                if (baseMediaTime != 0) {
                    time = (long)(baseMediaTime * FROM_NAROSECONDS);
                } /*else*/ if (locator.getMarkId() > 0) {
                    mark = locator.getMarkId();
                } /*else*/ if (locator.getPlayItemId() > 0) {
                    pi = locator.getPlayItemId();
                }

                if (!Libbluray.selectPlaylist(pl, pi, mark, time)) {
                    return new ConnectionErrorEvent(this);
                }

                updateTime(new Time(Libbluray.tellTime() * TO_SECONDS));

            } catch (Throwable e) {
                return new ConnectionErrorEvent(this);
            }
            return super.doPrefetch();
        }
    }

    protected ControllerErrorEvent doStart(Time at) {
        synchronized (this) {
            if (at != null) {
                try {
                    Libbluray.seekTime((long)(at.getSeconds() * FROM_SECONDS));
                } catch (Throwable e) {
                    return new ConnectionErrorEvent(this);
                }
            }

            try {
                Libbluray.selectRate(rate, true);
            } catch (Throwable e) {
                return new ConnectionErrorEvent(this);
            }

            at = new Time(Libbluray.tellTime() * TO_SECONDS);
            return super.doStart(at);
        }
    }

    protected ControllerErrorEvent doStop() {
        Libbluray.selectRate(0.0f, false);
        return super.doStop();
    }

    protected void doSeekTime(Time at) {
        synchronized (this) {
            if ((state == Prefetched) || (state == Started)) {
                try {
                    Libbluray.seekTime((long)(at.getSeconds() * FROM_SECONDS));
                } catch (Throwable e) {
                    return;
                }
                at = new Time(Libbluray.tellTime() * TO_SECONDS);
            }
            super.doSeekTime(at);
        }
    }

    protected void doSetRate(Float factor) {
        synchronized (this) {
            if (state == Started) {
                try {
                    Libbluray.selectRate(factor.floatValue());
                } catch (Throwable e) {
                    return;
                }
                if (state == Started) {
                    baseMediaTime = getMediaNanoseconds();
                    baseTime = getTimeBase().getNanoseconds();
                }
            }
            super.doSetRate(factor);
        }
    }

    /* notification from app */
    protected void updateRate(float rate) {
        synchronized (this) {
            if (state == Started) {
                baseMediaTime = getMediaNanoseconds();
                baseTime = getTimeBase().getNanoseconds();
            }
            super.updateRate(rate);
        }
    }

    protected void doChapterReach(int param) {
        ((PlaybackControlImpl)controls[9]).onChapterReach(param);
    }
    protected void doMarkReach(int param) {
        ((PlaybackControlImpl)controls[9]).onMarkReach(param);
    }

    protected void doPlaylistStart(int param) {
    }

    protected void doPlayItemReach(int param) {
        ((PlaybackControlImpl)controls[9]).onPlayItemReach(param);
        ((UOMaskTableControlImpl)controls[16]).onPlayItemReach(param);
    }

    protected void doAngleChange(int param) {
        ((AngleControlImpl)controls[0]).onAngleChange(param);
    }

    protected void doSubtitleChange(int param) {
        ((SubtitlingControlImpl)controls[15]).onSubtitleChange(param);
    }

    protected void doPiPChange(int param) {
        ((PiPControlImpl)controls[8]).onPiPChange(param);
    }

    protected void endOfMedia(int playlist) {
        synchronized (this) {
            if (locator == null || locator.getPlayListId() != playlist) {
                System.err.println("endOfMedia ignored: playlist does not match (" + playlist + " != " + locator.getPlayListId());
                return;
            }
        }

        super.endOfMedia(playlist);
    }

    protected BDLocator getLocator() {
        return locator;
    }

    protected PlaylistInfo getPlaylistInfo() {
        return pi;
    }

    protected TIClip getCurrentClipInfo() {
        synchronized (this) {
            int state = getState();
            if ((state != Prefetched) && (state != Started))
                return null;

            int playitem = RegisterAccess.getInstance().getPSR(RegisterAccess.PSR_PLAYITEM_ID);
            TIClip[] clips = pi.getClips();
            if (playitem >= clips.length)
                return null;
            return clips[playitem];
        }
    }

    protected void selectPlayList(BDLocator locator)
            throws InvalidPlayListException, InvalidLocatorException, ClockStartedError {
        synchronized (this) {
            if (getState() == Started)
                throw new ClockStartedError();
            if (!locator.isPlayListItem())
                throw new InvalidLocatorException(locator);
            pi = Libbluray.getPlaylistInfo(locator.getPlayListId());
            if (pi == null)
                throw new InvalidPlayListException();
            this.locator = locator;
            baseMediaTime = 0;
            if (state == Prefetched)
                doPrefetch();
        }
    }

    protected void seekMark(int mark) throws IllegalArgumentException {
        if ((pi == null) || (mark < 0) || (mark >= pi.getMarkCount()))
            throw new IllegalArgumentException();
        PlaylistPlayerAction action = new PlaylistPlayerAction(
                this, PlaylistPlayerAction.ACTION_SEEK_MARK, mark);
        commandQueue.put(action);
        action.waitEnd();
    }

    protected void seekPlayItem(int item) throws IllegalArgumentException {
        if ((pi == null) || (item < 0) || (item >= pi.getClipCount()))
            throw new IllegalArgumentException();
        PlaylistPlayerAction action = new PlaylistPlayerAction(
                this, PlaylistPlayerAction.ACTION_SEEK_PLAYITEM, item);
        commandQueue.put(action);
        action.waitEnd();
    }

    private class PlaylistPlayerAction extends BDJAction {
        private PlaylistPlayerAction(Handler player, int action, int param) {
            this.player = player;
            this.action = action;
            this.param = param;
        }

        protected void doAction() {
            switch (action) {
            case ACTION_SEEK_MARK:
                if ((player.getState() == Prefetched) || (player.getState() == Started)) {
                    Libbluray.seekMark(param);
                    player.updateTime(new Time(Libbluray.tellTime() * TO_SECONDS));
                } else if (player.locator != null) {
                    player.locator.setMarkId(param);
                    player.locator.setPlayItemId(-1);
                }
                break;
            case ACTION_SEEK_PLAYITEM:
                if ((player.getState() == Prefetched) || (player.getState() == Started)) {
                    Libbluray.seekPlayItem(param);
                    player.updateTime(new Time(Libbluray.tellTime() * TO_SECONDS));
                } else if (player.locator != null) {
                    player.locator.setMarkId(-1);
                    player.locator.setPlayItemId(param);
                }
                break;
            }
        }

        private Handler player;
        private int action;
        private int param;

        public static final int ACTION_SEEK_MARK = 1;
        public static final int ACTION_SEEK_PLAYITEM = 2;
    }

    private PlaylistInfo pi = null;
}
