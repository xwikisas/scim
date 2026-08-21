/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.scim.rest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import org.apache.commons.httpclient.HttpMethodBase;

import org.xwiki.test.ui.TestUtils;

/**
 * Test utilities.
 * @version $Id$
 * @since 1.0.0-rc-7
 */
final class Utils
{
    private Utils()
    {
        // ignore
    }

    static JSONObject getJSONResponse(HttpMethodBase method) throws IOException, JSONException
    {
        return new JSONObject(new String(method.getResponseBody()));
    }

    static String getScimUsersEndpointURL(TestUtils setup)
    {
        return getScimEndpointURL(setup) + "Users";
    }

    static String getScimEndpointURL(TestUtils setup)
    {
        return setup.rest().getBaseURL() + "/scim/v2/";
    }

    static String getScimGroupsEndpointURL(TestUtils setup)
    {
        return setup.rest().getBaseURL() + "/scim/v2/" + "Groups";
    }

    static void updateConf(TestUtils setup, Object... values)
    {
        setup.updateObject(List.of("SCIM", "Code"), "Configuration", "SCIM.Code.ConfigurationClass", 0, values);
    }

    static void setUserNameCleaning(TestUtils setup, boolean enabled)
    {
        updateConf(setup, "usernameCleaningEnabled", enabled ? 1 : 0);
    }

    static void setScimAdmins(TestUtils setup, XWikiUser... users)
    {
        String usersStr = Arrays.stream(users)
            .map(user -> "XWiki." + user.userName())
            .collect(Collectors.joining(","));
        updateConf(setup, "users", usersStr, "advertisedAuthSchemes", "oauth2,httpbasic");
    }

}
