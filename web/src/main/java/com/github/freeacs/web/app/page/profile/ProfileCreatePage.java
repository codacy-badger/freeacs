package com.github.freeacs.web.app.page.profile;

import com.github.freeacs.dbi.Profile;
import com.github.freeacs.dbi.Unittype;
import com.github.freeacs.dbi.XAPS;
import com.github.freeacs.web.Page;
import com.github.freeacs.web.app.Output;
import com.github.freeacs.web.app.input.*;
import com.github.freeacs.web.app.menu.MenuItem;
import com.github.freeacs.web.app.util.SessionData;
import com.github.freeacs.web.app.util.WebConstants;
import com.github.freeacs.web.app.util.XAPSLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * What the name implies, a page for creating a profile.
 * 
 * @author Jarl Andre Hubenthal
 *
 */
public class ProfileCreatePage extends ProfileActions {
	
	/* (non-Javadoc)
	 * @see com.owera.xaps.web.app.page.AbstractWebPage#getShortcutItems(com.owera.xaps.web.app.util.SessionData)
	 */
	public List<MenuItem> getShortcutItems(SessionData sessionData){
		List<MenuItem> list = new ArrayList<MenuItem>();
		list.addAll(super.getShortcutItems(sessionData));
		list.add(new MenuItem("Profile overview",Page.PROFILEOVERVIEW));
		return list;
	}
	
	/* (non-Javadoc)
	 * @see com.owera.xaps.web.app.page.WebPage#process(com.owera.xaps.web.app.input.ParameterParser, com.owera.xaps.web.app.output.ResponseHandler)
	 */
	public void process(ParameterParser params, Output outputHandler) throws Exception {
		ProfileData inputData = (ProfileData) InputDataRetriever.parseInto(new ProfileData(), params);

		String sessionId = params.getSession().getId();

		XAPS xaps = XAPSLoader.getXAPS(sessionId);

		if (xaps == null) {
			outputHandler.setRedirectTarget(WebConstants.DB_LOGIN_URL);
			return;
		}

		InputDataIntegrity.loadAndStoreSession(params,outputHandler,inputData, inputData.getUnittype(), inputData.getProfile(), inputData.getUnit());

		Map<String, Object> root = outputHandler.getTemplateMap();
		
		DropDownSingleSelect<Unittype> unittypes = InputSelectionFactory.getUnittypeSelection(inputData.getUnittype(), xaps);
		
		DropDownSingleSelect<Profile> profiles = InputSelectionFactory.getProfileSelection(inputData.getProfile(),inputData.getUnittype(), xaps);

		DropDownSingleSelect<Profile> profilestocopyfrom = InputSelectionFactory.getProfileSelection(inputData.getProfileCopy(), inputData.getUnittype(), xaps);
		
		root.put("unittypes", unittypes);
		
		if (unittypes.getSelected() != null)
			root.put("profilestocopyfrom",profilestocopyfrom);
		
		if (inputData.getFormSubmit().isValue("Create profile")) {
			if (isProfilesLimited(unittypes.getSelected(), sessionId)) {
				throw new Exception("You are not allowed to create profiles!");
			}
			if (unittypes.getSelected() != null && unittypes.getSelected().getProfiles().getByName(inputData.getProfilename().getString()) == null) {
				ProfileStatus status = actionCreateProfile(sessionId, inputData, xaps, unittypes, profiles);
				if (status == ProfileStatus.PROFILE_CREATED) {
					outputHandler.setDirectToPage(Page.PROFILE);
					return;
				}else if(status == ProfileStatus.PROFILE_NAME_UNSPECIFIED){
					root.put("error","The profile name was not specified");
				}
			} else
				root.put("error", "The profile " + inputData.getProfilename().getString() + " already exists.");
		}
		
		outputHandler.setTemplatePath("/profile/create");
	}
}