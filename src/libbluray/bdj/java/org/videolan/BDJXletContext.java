/*
 * This file is part of libbluray
 * Copyright (C) 2010  William Hahne
 * Copyright (C) 2013  Petri Hintukainen <phintuka@users.sourceforge.net>
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
package org.videolan;

import java.awt.Container;
import java.awt.EventQueue;
import java.net.URL;
import java.util.LinkedList;
import java.util.HashMap;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.microedition.xlet.UnavailableContainerException;

import org.bluray.ui.FrameAccurateAnimation;
import org.dvb.application.AppID;
import org.dvb.application.AppProxy;
import org.dvb.application.AppsDatabase;
import org.havi.ui.HSceneFactory;
import org.videolan.bdjo.AppCache;
import org.videolan.bdjo.AppEntry;

public class BDJXletContext implements javax.tv.xlet.XletContext, javax.microedition.xlet.XletContext {
    public BDJXletContext(AppEntry entry, AppCache[] caches, Container container) {
        this.appid = entry.getIdentifier();
        this.args = entry.getParams();
        this.loader = BDJClassLoader.newInstance(
                caches,
                entry.getBasePath(),
                entry.getClassPathExt(),
                entry.getInitialClass());
        this.container = container;

        this.threadGroup = new BDJThreadGroup(Integer.toHexString(appid.getOID()) + "." +
                                              Integer.toHexString(appid.getAID()) + "." +
                                              entry.getInitialClass(),
                                              this);

        callbackQueue = new BDJActionQueue(this.threadGroup, "CallbackQueue");
    }

    public Object getXletProperty(String key) {
        if (key.equals(javax.tv.xlet.XletContext.ARGS) ||
            key.equals(javax.microedition.xlet.XletContext.ARGS))
            return args;
        else if (key.equals("dvb.org.id"))
            return Integer.toHexString(appid.getOID());
        else if (key.equals("dvb.app.id"))
            return Integer.toHexString(appid.getAID());
        else if (key.equals("org.dvb.application.appid"))
            return appid;
        return null;
    }

    public void notifyDestroyed() {
        AppProxy proxy = AppsDatabase.getAppsDatabase().getAppProxy(appid);
        if (proxy instanceof BDJAppProxy)
            ((BDJAppProxy)proxy).notifyDestroyed();
    }

    public void notifyPaused() {
        AppProxy proxy = AppsDatabase.getAppsDatabase().getAppProxy(appid);
        if (proxy instanceof BDJAppProxy)
            ((BDJAppProxy)proxy).notifyPaused();
    }

    public void resumeRequest() {
        AppProxy proxy = AppsDatabase.getAppsDatabase().getAppProxy(appid);
        if (proxy instanceof BDJAppProxy)
            ((BDJAppProxy)proxy).resume();
    }

    public Container getContainer() throws UnavailableContainerException {
        if (container == null) {
            logger.error("getContainer(): container is null");
            throw new UnavailableContainerException();
        }
        return container;
    }

    /*
     * Class loader
     */

    public ClassLoader getClassLoader() {
        return loader;
    }

    public static ClassLoader getCurrentClassLoader() {
        BDJXletContext ctx = BDJXletContext.getCurrentContext();
        if (ctx == null) {
            logger.error("getCurrentClassLoader(): no context: " + Logger.dumpStack());
            return null;
        }

        ClassLoader cldr = (ClassLoader)ctx.getClassLoader();
        if (cldr == null) {
            logger.error("getCurrentClassLoader(): no class loader: " + Logger.dumpStack());
            return null;
        }

        return cldr;
    }

    public static URL getCurrentResource(String path)
    {
        ClassLoader cldr = (ClassLoader)BDJXletContext.getCurrentClassLoader();
        if (cldr == null) {
            return null;
        }

        if (path.startsWith("./") || path.startsWith(".\\")) {
            path = path.substring(2);
        }

        URL url = cldr.getResource(path);
        if (url == null) {
            logger.error("getCurrentResource(): " + path + " not found: " + Logger.dumpStack());
            return null;
        }

        return url;
    }

    /*
     *
     */

    protected AppProxy getAppProxy() {
        return AppsDatabase.getAppsDatabase().getAppProxy(appid);
    }

    public boolean isReleased() {
        return released;
    }

    /*
    public boolean isDestroyed() {
        if (released)
            return true;
        AppProxy proxy = AppsDatabase.getAppsDatabase().getAppProxy(appid);
        return proxy == null || proxy.getState() == AppProxy.DESTROYED;
    }
    */

    public BDJThreadGroup getThreadGroup() {
        return threadGroup;
    }

    /*
     * Event queues
     */

    protected void setEventQueue(EventQueue eq) {
        eventQueue = eq;
    }

    public EventQueue getEventQueue() {
        return eventQueue;
    }

    public boolean putCallback(BDJAction cb)
    {
        synchronized (this) {
            if (isReleased()) {
                logger.error("callback ignored (xlet destroyed)");
                return false;
            }
            if (callbackQueue == null) {
                logger.error("callback ignored (no queue)");
                return false;
            }
            callbackQueue.put(cb);
            return true;
        }
    }

    protected int numEventQueueThreads() {
        int cnt = 0;
        if (eventQueue != null) {
            Thread t = java.awt.BDJHelper.getEventDispatchThread(eventQueue);
            if (t != null && t.isAlive()) {
                cnt++;
            }
        }
        if (!released) {
            // callbackQueue
            cnt++;
        }
        return cnt;
    }

    /*
     * HAVI
     */

    public void setSceneFactory(HSceneFactory f) {
        sceneFactory = f;
    }

    public HSceneFactory getSceneFactory() {
        return sceneFactory;
    }

    public static Object getXletDefaultLook(String key, Class defClass) {
        BDJXletContext ctx = BDJXletContext.getCurrentContext();
        if (ctx == null) {
            logger.error("getDefaultLook(): no context: " + Logger.dumpStack());
            return null;
        }
        return ctx.getDefaultLook(key, defClass);
    }

    public static void setXletDefaultLook(String key, Object look) {
        BDJXletContext ctx = BDJXletContext.getCurrentContext();
        if (ctx == null) {
            logger.error("setDefaultLook(): no context: " + Logger.dumpStack());
            return;
        }
        ctx.setDefaultLook(key, look);
    }

    private Object getDefaultLook(String key, Class defClass) {
        Object look = null;
        synchronized (defaultLooks) {
            look = defaultLooks.get(key);
            if (look == null) {
                try {
                    look = defClass.newInstance();
                    setDefaultLook(key, look);
                } catch (Throwable t) {
                    logger.error("Error creating default look " + defClass.getName() + " for " + key + ": " + t);
                }
            }
        }
        return look;
    }

    private void setDefaultLook(String key, Object look) {
        synchronized (defaultLooks) {
            defaultLooks.remove(key);
            if (look != null) {
                defaultLooks.put(key, look);
            }
        }
    }

    /*
     * Ixc
     */

    protected void addIxcThread(Thread thread) {
        synchronized (ixcThreads) {
            ixcThreads.addLast(thread);
        }
    }
    protected void removeIxcThread(Thread thread) {
        synchronized (ixcThreads) {
            ixcThreads.remove(thread);
        }
    }

    private static boolean waitThread(Thread thread, int timeout) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout;
        while (thread.isAlive() && (System.currentTimeMillis() < endTime)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) { }
        }

        boolean result = !thread.isAlive();
        if (!result) {
            logger.error("waitThread timeout: " + thread);
        }
        return result;
    }

    public static void stopThread(Thread thread, int timeout, String type) {
        if (!waitThread(thread, timeout)) {
            thread.interrupt();
            if (!waitThread(thread, 200)) {
                PortingHelper.stopThread(thread);
                logger.error("killing " + type + " thread " + thread);
            }
        }
        try {
            thread.join();
        } catch (Throwable t) { }
    }

    protected void stopIxcThreads() {
        while (true) {
            Thread thread;
            synchronized (ixcThreads) {
                if (ixcThreads.isEmpty()) {
                    break;
                }
                thread = (Thread)ixcThreads.removeFirst();
            }
            logger.info("Stopping remote thread " + thread);
            stopThread(thread, 1000, "Ixc");
            removeIxcThread(thread);
        }
    }

    /*
     * Frame-accurate animations
     */

    public void addFAA(FrameAccurateAnimation faa) {
        synchronized (faaList) {
            faaList.add(faa);
        }
    }

    public void removeFAA(FrameAccurateAnimation faa) {
        synchronized (faaList) {
            faaList.remove(faa);
        }
    }

    public void removeAllFAA() {
        Object[] faaArray;
        synchronized (faaList) {
            faaArray = faaList.toArray();
        }
        for (int i = 0; i < faaArray.length; i++) {
            FrameAccurateAnimation faa = (FrameAccurateAnimation)faaArray[i];
            faa.destroy();
        }
    }

    /*
     *
     */

    public static BDJXletContext getCurrentContext() {
        Object obj = AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        ThreadGroup group = Thread.currentThread().getThreadGroup();
                        while ((group != null) && !(group instanceof BDJThreadGroup))
                            group = group.getParent();
                        return group;
                    }
                }
            );
        if (obj == null)
            return null;
        return ((BDJThreadGroup)obj).getContext();
    }

    protected void setArgs(String[] args) {
        this.args = args;
    }

    protected void update(AppEntry entry, AppCache[] caches) {
        args = entry.getParams();
        loader.update(
                caches,
                entry.getBasePath(),
                entry.getClassPathExt(),
                entry.getInitialClass());
    }

    protected void release() {

        removeAllFAA();
        stopIxcThreads();
        defaultLooks.clear();

        org.dvb.io.ixc.IxcRegistry.unbindAll(this);

        if (sceneFactory != null) {
            sceneFactory.dispose();
            sceneFactory = null;
        }

        callbackQueue.shutdown();

        EventQueue eq = eventQueue;
        eventQueue = null;
        if (eq != null) {
            java.awt.BDJHelper.stopEventQueue(eq);
        }

        threadGroup.stopAll(1000);

        synchronized (this) {
            threadGroup = null;
            loader = null;
            container = null;
            callbackQueue = null;
            defaultLooks = null;
            released = true;
        }
    }

    private boolean released = false;
    private String[] args;
    private AppID appid;
    private BDJClassLoader loader;
    private Container container;
    private EventQueue eventQueue = null;
    private HSceneFactory sceneFactory = null;
    private BDJThreadGroup threadGroup = null;
    private LinkedList ixcThreads = new LinkedList();
    private LinkedList faaList = new LinkedList();
    private HashMap defaultLooks = new HashMap();
    private BDJActionQueue callbackQueue;
    private static final Logger logger = Logger.getLogger(BDJXletContext.class.getName());
}
