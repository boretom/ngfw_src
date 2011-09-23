package com.untangle.node.webfilter;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.hibernate.Query;
import org.hibernate.Session;

import com.untangle.node.http.RequestLine;
import com.untangle.uvm.logging.SimpleEventFilter;
import com.untangle.uvm.logging.ListEventFilter;
import com.untangle.uvm.logging.RepositoryDesc;
import com.untangle.uvm.util.I18nUtil;

/**
 * Filter for passed HTTP traffic.
 *
 * @author <a href="mailto:amread@untangle.com">Aaron Read</a>
 * @version 1.0
 */
public class WebFilterPassedFilter implements SimpleEventFilter<WebFilterEvent>
{
    private final String evtQuery;

    private static final RepositoryDesc REPO_DESC
        = new RepositoryDesc(I18nUtil.marktr("Flagged Web Traffic"));

    private final String vendorName;
    private final String capitalizedVendorName;

    public WebFilterPassedFilter(WebFilterBase node)
    {
        this.vendorName = node.getVendor();
        this.capitalizedVendorName = vendorName.substring(0, 1).toUpperCase() + 
            vendorName.substring(1);

        evtQuery = "FROM HttpLogEventFromReports evt " + 
            "WHERE evt.wf" + capitalizedVendorName + "Category IS NOT NULL " + 
            "AND evt.wf" + capitalizedVendorName + "Reason = 'I' " + 
            "AND evt.policyId = :policyId ";
    }

    public RepositoryDesc getRepositoryDesc()
    {
        return REPO_DESC;
    }

    public boolean accept(WebFilterEvent e)
    {
        return !e.getBlocked();
    }

    public String[] getQueries()
    {
        return new String[] { evtQuery }; 
    }
}
