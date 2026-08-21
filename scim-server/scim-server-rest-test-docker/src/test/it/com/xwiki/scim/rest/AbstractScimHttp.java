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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.apache.commons.lang3.Strings;
import org.apache.directory.scim.protocol.Constants;
import org.apache.directory.scim.spec.extension.EnterpriseExtension;
import org.apache.http.entity.ContentType;

import org.codehaus.plexus.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;

import static com.xwiki.scim.rest.Constants.SCIM_ADMIN2;
import static com.xwiki.scim.rest.Constants.USER;
import static com.xwiki.scim.rest.Utils.getJSONResponse;
import static com.xwiki.scim.rest.Utils.getScimEndpointURL;
import static com.xwiki.scim.rest.Utils.getScimGroupsEndpointURL;
import static com.xwiki.scim.rest.Utils.getScimUsersEndpointURL;
import static com.xwiki.scim.rest.Utils.updateConf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Inspired of org.xwiki.rest.test.framework.AbstractHttpIT.
 */
abstract class AbstractScimHttp extends BaseSCIMRestIT
{
    protected static final String ID = "id";
    protected static final String GROUP = "Group";
    protected static final String SCHEMAS = "schemas";
    protected static final String VALUE = "value";
    protected static final String MEMBERS = "members";
    protected static final String SCIM_SCHEMA_PREFIX = "urn:ietf:params:scim:schemas:core:2.0:";
    protected static final Set<String> SCHEMA_ATTRIBUTES = Set.of("name", "type", "multiValued", "description",
        "required", "caseExact", "mutability", "returned", "uniqueness");

    protected static final String XWIKI_SPACE = "XWiki";
    protected static final String XWIKI_WIKI = "xwiki";
    protected static final String SCIM = "SCIM";
    protected static final String RESOURCES = "Resources";

    protected static final String OIDC_ISSUER = "https://perdu.com/oidc/";

    private static final Comparator<JSONObject> COMPARING_GROUP_MEMBERS = Comparator.comparing(m -> {
        try {
            return m.getString(VALUE);
        } catch (JSONException e) {
            fail(e);
            return "";
        }
    });


    protected static JSONObject clone(JSONObject o) throws JSONException
    {
        JSONObject r = new JSONObject();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            r.put(key, o.get(key));
        }

        return r;
    }

    protected static void sortArray(JSONObject o, String k, Comparator<JSONObject> c) throws JSONException
    {
        JSONArray toSort = o.getJSONArray(k);
        List<JSONObject> sorted = new ArrayList<>(toSort.length());
        for (int i = 0; i < toSort.length(); i++) {
            sorted.add(toSort.getJSONObject(i));
        }
        sorted.sort(c);
        o.put(k, new JSONArray(sorted));
    }

    protected static void assertResourceSchemaOK(TestUtils setup, String resourceType, JSONObject actual)
        throws JSONException
    {
        assertSchemaOK(getExpectedResourceSchema(setup, resourceType), actual);
    }

    protected static JSONObject getExpectedResourceSchema(TestUtils setup, String resourceType)
        throws JSONException
    {
        String urn = "urn:ietf:params:scim:schemas:core:2.0:" + resourceType;
        String description = "Top level Scim" + resourceType;
        return getExpectedSchema(setup, urn, resourceType, description);
    }

    protected static JSONObject getExpectedEnterpriseUserSchema(TestUtils setup) throws JSONException
    {
        return getExpectedSchema(
            setup,
            EnterpriseExtension.URN,
             "EnterpriseUser",
            "Attributes commonly used in representing users that belong to, or act on behalf of, a business or enterprise."
        );
    }

    private static JSONObject getExpectedSchema(TestUtils setup, String urn, String name, String description)
        throws JSONException
    {
        return newJSONObject(
            "meta", new JSONObject(Map.of(
                "resourceType", "Schema",
                "location", getScimEndpointURL(setup) + "Schemas/" + urn
            )),
            "id", urn,
            "name", name,
            "description", description
        );
    }

    protected static JSONObject withResourceTypeFields(JSONObject rt) throws JSONException
    {
        String name = (String) rt.get("name");
        JSONArray schemaExtensions = "User".equals(name)
             ? new JSONArray(new JSONObject[] {
                 new JSONObject(Map.of(
                     "schema", EnterpriseExtension.URN,
                     "required", false))
             })
             : new JSONArray();

        return putAll(rt,
            "meta", new JSONObject(Map.of(
                "resourceType", "ResourceType",
                "location", ((String) rt.getJSONObject("meta").get("location")).replace(
                        "Schemas/urn:ietf:params:scim:schemas:core:2.0:", "ResourceTypes/")
                )),
            "endpoint", "/" + name + 's',
            "schemaExtensions", schemaExtensions,
            "schemas", new JSONArray(new String[] { "urn:ietf:params:scim:schemas:core:2.0:ResourceType" } ),
            "schema", rt.get("id"),
            "id", name
        );
    }

    protected static void assertSchemaOK(JSONObject expected, JSONObject actual)
        throws JSONException
    {
        JSONArray attributes = (JSONArray) actual.remove("attributes");
        assertAttributesOK(attributes);

        JSONAssert.assertEquals(expected, actual, true);
    }

    private static void assertAttributesOK(JSONArray attributes) throws JSONException
    {
        assertJSONNotNull(attributes);
        assertTrue(attributes.length() > 0);
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject attribute = attributes.getJSONObject(i);
            JSONArray subAttributes = (JSONArray) attribute.remove("subAttributes");
            JSONArray canonicalValues = (JSONArray) attribute.remove("canonicalValues");
            JSONArray referenceTypes = (JSONArray) attribute.remove("referenceTypes");
            String type = attribute.getString("type");
            switch (type) {
                case "reference":
                    assertJSONNotNull(referenceTypes);
                    assertTrue(referenceTypes.length() > 0);
                    for (int j = 0; j < referenceTypes.length(); j++) {
                        String referenceType = referenceTypes.getString(j);
                        assertTrue(List.of("external", "User", "Group").contains(referenceType));
                    }
                    assertJSONNull(canonicalValues);
                    assertJSONNull(subAttributes);
                    break;
                case "complex":
                    assertJSONNull(referenceTypes);
                    assertJSONNull(canonicalValues);
                    assertJSONNotNull(subAttributes);
                    assertAttributesOK(subAttributes);
                    break;
                default:
                    assertJSONNull(referenceTypes);
                    if ("type".equals(attribute.get("name"))) {
                        assertEquals("string", type);
                        if (canonicalValues != null /*&& !(canonicalValues instanceof JSONNull)*/) {
                            assertTrue(canonicalValues.length() > 0);
                        }
                    } else {
                        assertJSONNull(canonicalValues);
                    }
                    assertJSONNull(subAttributes);
            }
            Set<String> attrs = new HashSet<>(attribute.length());
            Iterator<String> keys = attribute.keys();
            while (keys.hasNext()) {
                attrs.add(keys.next());
            }
            assertEquals(SCHEMA_ATTRIBUTES, attrs);
        }
    }

    private static String assertUserEqual(JSONObject userSpec, JSONObject actual, String userName, String version)
        throws JSONException
    {
        boolean active = userSpec.optBoolean("active", true);
        assertJSONNotNull(actual.remove("groups"));
        JSONObject receivedMeta = actual.getJSONObject("meta");
        assertJSONNotNull(receivedMeta.remove("created"));
        assertJSONNotNull(receivedMeta.remove("lastModified"));
        String receivedId = actual.getString(ID);
        if (!actual.has("active")) {
            actual.put("active", true);
        }
        JSONObject expected = newJSONObject(
            "meta", new JSONObject(Map.of(
                "resourceType", USER,
                "location", "http://localhost:8080/xwiki/rest/scim/v2/Users/" + receivedId,
                "version", getExpectedVersion(actual, version)
            )),
            ID, receivedId,
            "active", active,
            "displayName", userSpec.optString("displayName", ""),
            "userName", userName,
            EnterpriseExtension.URN, userSpec.opt(EnterpriseExtension.URN),
            SCHEMAS, new JSONArray(new String[] {"urn:ietf:params:scim:schemas:core:2.0:User", EnterpriseExtension.URN})
        );

        Object externalIdObj = userSpec.opt("externalId");
        if (externalIdObj instanceof String externalId) {
            expected.put("externalId", externalId);
        }

        expected.put("userType", userSpec.opt("userType"));
        expected.put("profileUrl", userSpec.opt("profileUrl"));
        expected.put("nickName", userSpec.opt("nickName"));
        keepRoles(expected, userSpec);

        keepLists(expected, userSpec, "phoneNumbers", "addresses", "ims", "emails", "photos");
        keepSingle(expected, userSpec, "title", "timezone", "locale");
        expected.put("preferredLanguage", userSpec.opt("preferredLanguage"));
        expected.put("name", userSpec.opt("name"));
        for (String key : List.of(
            "entitlements", "nickName", "password", "photos", "profileUrl", "roles", "userType",
            "x509Certificates", "addresses", "emails", "ims", "locale", "name", "phoneNumbers", "preferredLanguage",
            "timezone", "title"
        )) {
            Object v = actual.opt(key);
            if (v == null || (v instanceof JSONArray a && a.length() == 0)) {
                actual.remove(key);
            }
        }

        JSONAssert.assertEquals(expected, actual, true);
        return receivedId;
    }

    private static String getExpectedVersion(JSONObject actual, String version) throws JSONException
    {
        if (version == null) {
            JSONObject meta = (JSONObject) actual.opt("meta");
            if (meta != null && meta.opt("version") instanceof String actualVersion) {
                return actualVersion;
            }
            // ???
            return "1.1";
        }
        return version + ".1";
    }

    private static void keepRoles(JSONObject expected, JSONObject userSpec) throws JSONException
    {
        Object rolesObj = userSpec.opt("roles");
        if (rolesObj instanceof JSONArray roles) {
            for (int i = 0; i < roles.length(); i++) {
                JSONObject role = roles.getJSONObject(i);
                Object primary = role.opt("primary");
                if (primary instanceof String primaryStr) {
                    role.put("primary", Boolean.parseBoolean(primaryStr));
                }
            }
            expected.put("roles", roles);
        }
    }

    private static void keepSingle(JSONObject expected, JSONObject userSpec, String... fields) throws JSONException
    {
        for (String field : fields) {
            Object v = userSpec.optString(field, null);
            if (v != null) {
                expected.put(field, v);
            }
        }
    }

    private static void keepLists(JSONObject expected, JSONObject userSpec, String... fields) throws JSONException
    {
        // Our implementation currently only keeps the primary value of list fields
        // this method reflects this in expected values
        for (String field : fields) {
            JSONArray fullSpec = userSpec.optJSONArray(field);
            if (fullSpec == null) {
                continue;
            }

            JSONArray newValue = new JSONArray();
            for (int i = 0; i < fullSpec.length(); i++) {
                JSONObject m = fullSpec.getJSONObject(i);
                if (m.has("primary")) {
                    newValue.put(m);
                } else {
                    JSONObject res = clone(m);
                    res.put("primary", false);
                    newValue.put(res);
                }
            }
            expected.put(field, newValue);
        }
    }

    private static String assertGroupEqual(JSONObject groupSpec, JSONObject actual, String displayName, String version)
        throws JSONException
    {
        boolean active = groupSpec.optBoolean("active", true);
        actual.remove("timezone");
        JSONObject receivedMeta = actual.getJSONObject("meta");
        assertJSONNotNull(receivedMeta.remove("created"));
        assertJSONNotNull(receivedMeta.remove("lastModified"));
        String receivedId = actual.optString(ID, null);
        if (!actual.has("active")) {
            actual.put("active", true);
        }
        JSONObject expected = new JSONObject(Map.of(
            "meta", new JSONObject(Map.of(
                "resourceType", GROUP,
                "location", "http://localhost:8080/xwiki/rest/scim/v2/Groups/" + receivedId,
                "version", version + ".1"
            )),
            ID, receivedId,
            "externalId", groupSpec.opt("externalId"),
            "active", active,
            "displayName", displayName,
            SCHEMAS, new JSONArray(new String[] {"urn:ietf:params:scim:schemas:core:2.0:Group"})
        ));
        if (actual.has(MEMBERS)) {
            expected.put(MEMBERS, groupSpec.getJSONArray(MEMBERS));
            sortArray(actual, MEMBERS, COMPARING_GROUP_MEMBERS);
            sortArray(expected, MEMBERS, COMPARING_GROUP_MEMBERS);
        }
        JSONAssert.assertEquals(expected, actual, true);
        return receivedId;
    }

    private static void assertJSONNull(Object v)
    {
        assertNull(v);
    }

    private static void assertJSONNotNull(Object v)
    {
        assertNotNull(v);
    }

    protected static String getSCIMUserURL(TestUtils setup, String id)
    {
        return getScimUsersEndpointURL(setup) + "/" + id;
    }

    private static String getSCIMGroupURL(TestUtils setup, String id)
    {
        return getScimGroupsEndpointURL(setup) + "/" + id;
    }

    private static void assertNotFound(HttpMethodBase method)
    {
        assertEquals(404, method.getStatusCode());
    }

    protected static void setOIDC(TestUtils setup, boolean enabled, String subject)
    {
        updateConf(setup, "oidcEnabled", enabled, "oidcSubject", subject, "oidcIssuer", OIDC_ISSUER);
    }

    private static void assertZeroResults(GetMethod getMethod) throws IOException, JSONException
    {
        JSONObject expected = getListResponseJSONObject(0);
        JSONObject actual = getJSONResponse(getMethod);
        assertEqualsIgnoreNulls(expected, actual);
    }

    private static JSONObject getListResponseJSONObject(int totalResults) throws JSONException
    {
        JSONObject expected = new JSONObject();
        expected.put("totalResults", totalResults);
        expected.put("startIndex", totalResults == 0 ? null : 1);
        expected.put("itemsPerPage", totalResults == 0 ? null : totalResults);
        expected.put(SCHEMAS, new JSONArray(new String[] {"urn:ietf:params:scim:api:messages:2.0:ListResponse"}));
        return expected;
    }

    private static void assertEqualsIgnoreNulls(JSONObject expected, JSONObject actual) throws JSONException
    {
        removeKeysWithNullValues(actual);
        removeKeysWithNullValues(expected);
        JSONAssert.assertEquals(expected, actual, true);
    }

    private static void removeKeysWithNullValues(JSONObject o) throws JSONException
    {
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (o.get(key) == null) {
                keys.remove();
            }
        }
    }

    protected void assertCorrectRightsOnResourceDocument(TestUtils setup, String resourceId, String type) throws Exception
    {
        String userResourceURL = setup.getURL(new DocumentReference(
            setup.getCurrentWiki(), List.of(SCIM, RESOURCES, scimAdmin().userName(), type), resourceId));
        // For some reason, the host in the rest base URL works and not the global baseURL, so we use that instead.
        userResourceURL = Strings.CS.removeEnd(setup.rest().getBaseURL(), "rest")
                + Strings.CS.removeStart(userResourceURL, setup.getBaseURL());
        GetMethod getAsAdmin = executeGet(userResourceURL, scimAdmin());
        assertOK(getAsAdmin);
        GetMethod getAsAdmin2 = executeGet(userResourceURL, SCIM_ADMIN2);
        assertForbidden(getAsAdmin2);
    }

    protected String assertCreateUserOK(TestUtils setup, JSONObject newUser, XWikiUser scimAdmin, String userName) throws Exception
    {
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin, newUser);
        assertOK(postMethod);
        return assertUserEqual(newUser, postMethod, userName, "1");
    }

    protected void assertPutUserOK(TestUtils setup, String userId, XWikiUser scimAdmin, JSONObject putUser,
                                   String userName, String version) throws Exception
    {
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + userId, scimAdmin, putUser);
        assertOK(putMethod);
        assertUserEqual(putUser, putMethod, userName, version);
    }

    protected void assertPutGroupOK(TestUtils setup, String groupId, XWikiUser scimAdmin, JSONObject putGroup,
                                    String displayName, String version) throws Exception
    {
        PutMethod putMethod = executePut(getScimGroupsEndpointURL(setup) + "/" + groupId, scimAdmin, putGroup);
        assertOK(putMethod);
        assertGroupEqual(putGroup, putMethod, displayName, version);
    }

    private String assertUserEqual(JSONObject userSpec, HttpMethodBase method, String userName, String version)
        throws IOException, JSONException
    {
        return assertUserEqual(userSpec, getJSONResponse(method), userName, version);
    }

    private String assertGroupEqual(JSONObject groupSpec, HttpMethodBase method, String displayName, String version)
        throws IOException, JSONException
    {
        return assertGroupEqual(groupSpec, getJSONResponse(method), displayName, version);
    }

    protected String assertCreateGroupOK(TestUtils setup, JSONObject newGroup, XWikiUser scimAdmin)
        throws Exception
    {
        PostMethod postMethod = executePost(getScimGroupsEndpointURL(setup), scimAdmin, newGroup);
        assertOK(postMethod);
        return assertGroupEqual(newGroup, postMethod, newGroup.getString("displayName"), "1");
    }

    protected void assertDeleteUserOK(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        List<String> referenceSpace = List.of(SCIM, RESOURCES, scimAdmin.userName(), USER);
        assertTrue(setup.pageExists(referenceSpace, id));
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, id), scimAdmin);
        assertOK(deleteMethod);
        assertFalse(setup.pageExists(referenceSpace, id));
    }

    protected void assertDeleteGroupOK(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        List<String> referenceSpace = List.of(SCIM, RESOURCES, scimAdmin.userName(), GROUP);
        assertTrue(setup.pageExists(referenceSpace, id));
        DeleteMethod deleteMethod = executeDelete(getSCIMGroupURL(setup, id), scimAdmin);
        assertOK(deleteMethod);
        assertFalse(setup.pageExists(referenceSpace, id));
    }

    protected void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject userSpec)
        throws Exception
    {
        assertGetUserOK(setup, id, scimAdmin, userSpec, null);
    }

    protected void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject userSpec,
                                   String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertUserEqual(userSpec, getMethod, userSpec.getString("userName"), version);
    }

    protected void assertGetUserNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertNotFound(getMethod);
    }

    protected void assertGetGroupOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject groupSpec,
                                    String displayName, String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMGroupURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertGroupEqual(groupSpec, getMethod, displayName, version);
    }

    protected void assertDeleteUserNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, id), scimAdmin);
        assertNotFound(deleteMethod);
    }

    protected void assertDeleteGroupNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        DeleteMethod deleteMethod = executeDelete(getSCIMGroupURL(setup, id), scimAdmin);
        assertNotFound(deleteMethod);
    }

    protected void assertUsersListIsEmpty(TestUtils setup, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        assertZeroResults(getMethod);
    }

    protected void assertGroupsListIsEmpty(TestUtils setup, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getScimGroupsEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        assertZeroResults(getMethod);
    }

    protected void assertUsersListEquals(TestUtils setup, List<JSONObject> expectedUsers, XWikiUser scimAdmin)
        throws Exception
    {
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        JSONObject expected = getListResponseJSONObject(expectedUsers.size());
        JSONObject actual = getJSONResponse(getMethod);
        Object resourcesObj = actual.remove(RESOURCES);
        assertEqualsIgnoreNulls(expected, actual);
        JSONArray resources;
        if (resourcesObj == null) {
            resources = new JSONArray();
        } else if (resourcesObj instanceof JSONArray resourceList) {
            resources = resourceList;
        } else {
            fail();
            // unreachable, but this is to make null checkers happy
            resources = new JSONArray();
        }
        assertEquals(expectedUsers.size(), resources.length());
        for (int i = 0; i < expectedUsers.size(); i++) {
            JSONObject expectedUser = expectedUsers.get(i);
            assertUserEqual(expectedUser, resources.getJSONObject(i), expectedUser.getString("userName"), "1");
        }
    }

    protected static JSONObject newJSONObject(Object... values) throws JSONException
    {
        return putAll(new JSONObject(), values);
    }

    protected static JSONArray newJSONArray(Object... values) throws JSONException
    {
        return new JSONArray(values);
    }

    protected static JSONObject putAll(JSONObject o, Object... values) throws JSONException
    {
        for (int i = 0; i < values.length; i += 2) {
            if (values[i] instanceof String k) {
                o.put(k, values[i + 1]);
            } else {
                throw new RuntimeException("bad putAll call: a key is not a String");
            }
        }
        return o;
    }

    protected static JSONObject getUser(String userName, String displayName, String externalId)
    {
        try {
            JSONObject user = getResource(USER, displayName, externalId);
            return user.put("userName", userName);
        } catch (JSONException e) {
            fail(e);
            return null;
        }
    }

    protected static JSONObject getGroup(String groupName, String externalId, List<JSONObject> members)
        throws JSONException
    {
        JSONObject group = getResource(GROUP, groupName, externalId);
        List<JSONObject> memberships = new ArrayList<>(members.size());
        for (JSONObject m : members) {
            memberships.add(getMember(m));
        }
        return group.put(MEMBERS, new JSONArray(memberships));
    }

    protected static JSONObject getMember(JSONObject m) throws JSONException
    {
        String type = m.getJSONArray(SCHEMAS).getString(0).endsWith(GROUP) ? GROUP : USER;
        String display = m.optString("userName", null);
        if (display == null) {
            display = m.optString("displayName", "");
        }
        return newJSONObject(
                VALUE, m.opt(ID),
            "type", type,
            "$ref", "/xwiki/rest/scim/v2/" + type + "s/" + m.get(ID),
            "display", display
        );
    }

    private static JSONObject getResource(String resourceType, String displayName, String externalId)
        throws JSONException
    {
        JSONArray schemas = new JSONArray(
            "User".equals(resourceType)
                ? new String[] { SCIM_SCHEMA_PREFIX + resourceType, EnterpriseExtension.URN }
                : new String[] { SCIM_SCHEMA_PREFIX + resourceType }
        );
        JSONObject r = newJSONObject(
            SCHEMAS, schemas,
            "displayName", displayName,
            "meta", new JSONObject(Map.of(
                "resourceType", resourceType,
                "created", "2010-01-23T04:56:22Z",
                "lastModified", "2011-05-13T04:42:34Z"
            ))
        );
        if (StringUtils.isNotEmpty(externalId)) {
            r.put("externalId", externalId);
        }
        return r;
    }

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
            new UsernamePasswordCredentials(user.userName(), user.password()));
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
}
