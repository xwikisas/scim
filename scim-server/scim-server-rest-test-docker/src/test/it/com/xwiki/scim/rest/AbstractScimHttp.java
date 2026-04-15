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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.directory.scim.protocol.Constants;
import org.apache.http.entity.ContentType;
import org.json.JSONException;
import org.xwiki.test.ui.TestUtils;

import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inspired of org.xwiki.rest.test.framework.AbstractHttpIT.
 */
abstract class AbstractScimHttp
{

    static final class PatchMethod extends PostMethod
    {
        PatchMethod(String uri)
        {
            super(uri);
        }

        @Override
        public String getName()
        {
            return "PATCH";
        }
    }

    public static final ContentType SCIM_CONTENT_TYPE =
        ContentType.create(Constants.SCIM_CONTENT_TYPE, StandardCharsets.UTF_8);

    record XWikiUser(String userName, String password) { }

    String getHttpMethodInfo(HttpMethod method) throws Exception
    {
        return String.format("\nName: %s\nURI: %s\nStatus code: %d\nStatus text: %s", method.getName(), method.getURI(),
            method.getStatusCode(), method.getStatusText());
    }

    GetMethod executeGet(String uri, XWikiUser user) throws Exception
    {
        HttpClient httpClient = getHttpClient(user);
        GetMethod getMethod = new GetMethod(uri);
        getMethod.addRequestHeader(HttpHeaders.ACCEPT, Constants.SCIM_CONTENT_TYPE);
        httpClient.executeMethod(getMethod);

        return getMethod;
    }

    DeleteMethod executeDelete(String uri, XWikiUser user) throws Exception
    {
        HttpClient httpClient = getHttpClient(user);
        DeleteMethod deleteMethod = new DeleteMethod(uri);
        deleteMethod.addRequestHeader(HttpHeaders.ACCEPT, Constants.SCIM_CONTENT_TYPE);
        httpClient.executeMethod(deleteMethod);

        return deleteMethod;
    }

    PostMethod executePost(String uri, XWikiUser user, String json) throws Exception
    {
        PostMethod postMethod = new PostMethod(uri);
        return executePost(json, postMethod, user);
    }

    PatchMethod executePatch(String uri, XWikiUser user, String json) throws Exception
    {
        PatchMethod patchMethod = new PatchMethod(uri);
        return executePost(json, patchMethod, user);
    }

    private static <T extends PostMethod> T executePost(String json, T postMethod, XWikiUser user)
        throws IOException
    {
        HttpClient httpClient = getHttpClient(user);
        postMethod.setRequestEntity(new StringRequestEntity(
            json,
            Constants.SCIM_CONTENT_TYPE,
            StandardCharsets.UTF_8.name()));
        postMethod.addRequestHeader(HttpHeaders.ACCEPT, Constants.SCIM_CONTENT_TYPE);
        httpClient.executeMethod(postMethod);
        return postMethod;
    }

    PostMethod executePost(String uri, XWikiUser user, JSONObject json) throws Exception
    {
        return executePost(uri, user, json.toString());
    }

    PostMethod executePost(String uri, XWikiUser user, Map<?, ?> content) throws Exception
    {
        JSONObject json = new JSONObject(content);
        return executePost(uri, user, json.toString());
    }

    PatchMethod executePatch(String uri, XWikiUser user, JSONObject json) throws Exception
    {
        return executePatch(uri, user, json.toString());
    }

    PatchMethod executePatch(String uri, XWikiUser user, Map<?, ?> content) throws Exception
    {
        JSONObject json = new JSONObject(content);
        return executePatch(uri, user, json.toString());
    }
    PutMethod executePut(String uri, XWikiUser user, String json) throws Exception
    {
        HttpClient httpClient = getHttpClient(user);
        PutMethod putMethod = new PutMethod(uri);
        putMethod.addRequestHeader(HttpHeaders.ACCEPT, Constants.SCIM_CONTENT_TYPE);
        putMethod.setRequestEntity(new StringRequestEntity(
            json,
            Constants.SCIM_CONTENT_TYPE,
            StandardCharsets.UTF_8.name()));
        httpClient.executeMethod(putMethod);

        return putMethod;
    }

    PutMethod executePut(String uri, XWikiUser user, Map<?, ?> content) throws Exception
    {
        JSONObject json = new JSONObject(content);
        return executePut(uri, user, json.toString());
    }

    PutMethod executePut(String uri, XWikiUser user, JSONObject json) throws Exception
    {
        return executePut(uri, user, json.toString());
    }

    private static HttpClient getHttpClient(XWikiUser user)
    {
        HttpClient httpClient = new HttpClient();
        httpClient.getState().setCredentials(AuthScope.ANY,
            new UsernamePasswordCredentials(user.userName, user.password));
        httpClient.getParams().setAuthenticationPreemptive(true);
        return httpClient;
    }

    void assertOK(DeleteMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_NO_CONTENT, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertOK(PatchMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_OK, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertOK(PostMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_CREATED, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertOK(GetMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_OK, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertOK(PutMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_OK, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertForbidden(GetMethod method) throws Exception
    {
        assertEquals(HttpStatus.SC_FORBIDDEN, method.getStatusCode(), getHttpMethodInfo(method));
    }

    void assertUnauthorized(HttpMethodBase method) throws Exception
    {
        assertEquals(HttpStatus.SC_UNAUTHORIZED, method.getStatusCode(), getHttpMethodInfo(method));
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
}
