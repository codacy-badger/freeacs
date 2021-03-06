package com.github.freeacs.web.app.page;

import com.github.freeacs.web.app.Output;
import com.github.freeacs.web.app.input.ParameterParser;

/**
 * The Class ManagementDashboardPage.
 *
 * @author Jarl Andre Hubenthal
 */
public class ManagementDashboardPage  extends AbstractWebPage {
	/* (non-Javadoc)
	 * @see com.owera.xaps.web.app.page.WebPage#process(com.owera.xaps.web.app.input.ParameterParser, com.owera.xaps.web.app.output.ResponseHandler)
	 */
	@Override
	public void process(ParameterParser params, Output outputHandler) throws Exception {
		outputHandler.setTemplatePath("service/dashboard");
	}

}
