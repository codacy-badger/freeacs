package com.github.freeacs.ws.impl;

import com.github.freeacs.dbi.Profile;
import com.github.freeacs.dbi.Unittype;
import com.github.freeacs.ws.ArrayOfProfile;
import com.github.freeacs.ws.GetProfilesRequest;
import com.github.freeacs.ws.GetProfilesResponse;
import com.github.freeacs.ws.ProfileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;

public class GetProfiles {

	private static final Logger logger = LoggerFactory.getLogger(GetProfiles.class);

	private XAPSWS xapsWS;

	public GetProfilesResponse getProfiles(GetProfilesRequest gur) throws RemoteException {
		try {
			
			xapsWS = XAPSWSFactory.getXAPSWS(gur.getLogin());
			if (gur.getUnittype() == null || gur.getUnittype().getName() == null)
				throw XAPSWS.error(logger, "No unittype is specified");
			Unittype unittype = xapsWS.getUnittypeFromXAPS(gur.getUnittype().getName());
			com.github.freeacs.ws.Profile[] profileArray = null;
			if (gur.getProfile() == null || gur.getProfile().getName() == null) {
				Profile[] profileXAPSArr = unittype.getProfiles().getProfiles();
				//				List<Profile> allowedProfiles = xapsWS.getXAPS().getAllowedProfiles(unittype);
				//				profileArray = new com.owera.xapsws.Profile[allowedProfiles.size()];
				profileArray = new com.github.freeacs.ws.Profile[profileXAPSArr.length];
				int i = 0;
				for (Profile profileXAPS : profileXAPSArr)
					profileArray[i++] = ConvertXAPS2WS.convert(profileXAPS);
				//				for (int i = 0; i < allowedProfiles.size(); i++)
				//					profileArray[i] = ConvertXAPS2WS.convert(allowedProfiles.get(i));
			} else {
				profileArray = new com.github.freeacs.ws.Profile[1];
				Profile p = xapsWS.getProfileFromXAPS(unittype.getName(), gur.getProfile().getName());
				profileArray[0] = ConvertXAPS2WS.convert(p);
			}
			return new GetProfilesResponse(new ProfileList(new ArrayOfProfile(profileArray)));
		} catch (Throwable t) {
			if (t instanceof RemoteException)
				throw (RemoteException) t;
			else {
				throw XAPSWS.error(logger, t);
			}
		}

	}
}
