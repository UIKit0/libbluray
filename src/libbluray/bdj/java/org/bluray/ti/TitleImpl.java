package org.bluray.ti;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.tv.locator.Locator;
import javax.tv.service.SIRequest;
import javax.tv.service.SIRequestor;
import javax.tv.service.ServiceType;

import org.bluray.net.BDLocator;
import org.davic.net.InvalidLocatorException;
import org.videolan.Libbluray;
import org.videolan.TitleInfo;

public class TitleImpl implements Title {
    public TitleImpl(int titleNum) {
        this.titleNum = titleNum;
        this.titleInfo = Libbluray.getTitleInfo(titleNum);
        if (titleInfo == null)
            throw new Error("Invalid title " + titleNum);
    }

    public PlayList[] getPlayLists()
    {
        throw new Error("Not implemented");
    }

    public boolean hasAutoPlayList()
    {
        throw new Error("Not implemented");
    }

    public Locator getLocator()
    {
        String url = "bd://" + titleNum;
        try {
            return new BDLocator(url);
        } catch (InvalidLocatorException ex) {
            logger.log(Level.WARNING, "Invalid locator: " + url);
            return null;
        }
    }

    public String getName()
    {
        return "Title " + titleNum;
    }

    @Override
    public ServiceType getServiceType()
    {
        throw new Error("Not implemented");
    }

    @Override
    public boolean hasMultipleInstances()
    {
        return false;
    }

    @Override
    public SIRequest retrieveDetails(SIRequestor requestor)
    {
        throw new Error("Not implemented");
    }
    
    private int titleNum;
    private TitleInfo titleInfo;
    private static final Logger logger = Logger.getLogger(TitleImpl.class.getName());
}