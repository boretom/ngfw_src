/**
 * $Id$
 */
package com.untangle.node.spyware;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.untangle.node.http.BlockPageUtil;
import com.untangle.uvm.UvmContext;
import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.BrandingManager;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.node.NodeSettings;
import com.untangle.uvm.util.I18nUtil;

@SuppressWarnings("serial")
public class BlockPageServlet extends HttpServlet
{
    // HttpServlet methods ----------------------------------------------------
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        UvmContext uvm = UvmContextFactory.context();
        NodeManager nm = uvm.nodeManager();

        Map<String,String> i18n_map = UvmContextFactory.context().
            languageManager().getTranslations( "untangle-node-spyware" );

        Spyware node = (Spyware) nm.node( Long.parseLong(request.getParameter( "tid" )) );
        if ( node == null || !(node instanceof Spyware)) {
            response.sendError( HttpServletResponse.SC_NOT_ACCEPTABLE, I18nUtil.tr( "Feature is not installed.", i18n_map ));
            return;
        }

        SpywareBlockDetails blockDetails = null;
        String unblockMode = null;
        String nonce = request.getParameter("nonce");
        
        blockDetails = node.getBlockDetails(nonce);
        if (blockDetails == null) {
            response.sendError( HttpServletResponse.SC_NOT_ACCEPTABLE, I18nUtil.tr( "This request has expired.", i18n_map ));
            return;
        }
        unblockMode = node.getUnblockMode();

        SpywareBlockPageParameters params = new SpywareBlockPageParameters(blockDetails, unblockMode);

        BlockPageUtil.getInstance().handle(request, response, this, params);
    }
    
    private static class SpywareBlockPageParameters implements BlockPageUtil.BlockPageParameters
    {
        private final SpywareBlockDetails blockDetails;
        private final String unblockMode;

        public SpywareBlockPageParameters( SpywareBlockDetails blockDetails, String unblockMode )
        {
            this.blockDetails = blockDetails;
            this.unblockMode = unblockMode;
        }

        /* This is the name of the node to use when retrieving the I18N bundle */
        public String getI18n()
        {
            return "untangle-node-spyware";
        }
        
        /* Retrieve the page title (in the window bar) of the page */
        public String getPageTitle( BrandingManager bm, Map<String,String> i18n_map )
        {
            return I18nUtil.tr( "{0} | Spyware Blocker Warning", bm.getCompanyName(), i18n_map);
        }
        
        /* Retrieve the title (top of the pae) of the page */
        public String getTitle( BrandingManager bm, Map<String,String> i18n_map )
        {
            return "Spyware Blocker";
        }
        
        public String getFooter( BrandingManager bm, Map<String,String> i18n_map )
        {
            return I18nUtil.tr( "{0} Spyware Blocker", bm.getCompanyName(), i18n_map);
        }
        
        /* Return the name of the script file to load, or null if there is not a script. */
        public String getScriptFile()
        {
            return "spyware.js";
        }

        public String getAdditionalFields(Map<String,String> i18n_map)
        {
            return null;
        }
        
        /* Retrieve the description of why this page was blocked. */
        public String getDescription( BrandingManager bm, Map<String,String> i18n_map )
        {
            return I18nUtil.tr( "{0}This web page was blocked{1} because it contains malicious content.", new Object[]{ "<b>","</b>" }, i18n_map );
        }
    
        public SpywareBlockDetails getBlockDetails()
        {
            return this.blockDetails;
        }
    
        public String getUnblockMode()
        {
            return this.unblockMode;
        }
    }
}
