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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.apache.commons.httpclient.methods.GetMethod;

import com.fasterxml.jackson.databind.json.JsonMapper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@UITest(vnc = false)
//@UITest(vnc = false, servletEngine = org.xwiki.test.docker.junit5.servletengine.ServletEngine.EXTERNAL)
class ScimRestIT extends AbstractScimHttp
{
    private static final String GROUP1 = "Group1";
    private static final String SUB_GROUP = "SubGroup";
    private static final XWikiUser SCIM_ADMIN = new XWikiUser("ScimAdmin", "scimPassword");
    private static final XWikiUser SCIM_ADMIN2 = new XWikiUser("ScimAdmin2", "scimPassword");
    private static final String NEWUSER = "newUser";
    private static final String XWIKI_SPACE = "XWiki";
    private static final String XWIKI_WIKI = "xwiki";
    private static final DocumentReference NEWUSER_REF = new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, NEWUSER);
    private static final JSONObject NEW_USER = getUser(NEWUSER, "New User", "newUserExternalId");
    private static final String ID = "id";
    private static final String GROUP = "Group";
    private static final String USER = "User";
    private static final String SCHEMAS = "schemas";
    private static final String VALUE = "value";
    private static final String MEMBERS = "members";
    private static final String SCIM_SCHEMA_PREFIX = "urn:ietf:params:scim:schemas:core:2.0:";

    private static final Set<String> SCHEMA_ATTRIBUTES = Set.of("name", "type", "multiValued", "description",
        "required", "caseExact", "mutability", "returned", "uniqueness");

    private  static final Comparator<JSONObject> COMPARING_WITH_ID = Comparator.comparing(m -> {
        try {
            return m.getString(ID);
        } catch (JSONException e) {
            fail(e);
            return "";
        }
    });

    private static final Comparator<JSONObject> COMPARING_GROUP_MEMBERS = Comparator.comparing(m -> {
        try {
            return m.getString(VALUE);
        } catch (JSONException e) {
            fail(e);
            return "";
        }
    });

    private static final String AUTHORIZATION = "Basic WFdpa2kuU2NpbUFkbWluOnNjaW1QYXNzd29yZA==";

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";
    private static final String USER_3 = "user3";
    private static final String USER_4 = "user4";

    private static final String RESOURCES = "Resources";

    private static final String OIDC_ISSUER = "https://perdu.com/oidc/";

    private static JSONObject getUser(String userName, String displayName, String externalId)
    {
        try {
            JSONObject user = getResource(USER, displayName, externalId);
            user.put("userName", userName);
            return user;
        } catch (JSONException e) {
            fail(e);
            return null;
        }
    }

    private static JSONObject getGroup(String groupName, String externalId, List<JSONObject> members)
        throws JSONException
    {
        JSONObject group = getResource(GROUP, groupName, externalId);
        List<JSONObject> memberships = new ArrayList<>(members.size());
        for (JSONObject m : members) {
            memberships.add(getMember(m));
        }
        group.put(MEMBERS, new JSONArray(memberships));
        return group;
    }

    private static JSONObject getMember(JSONObject m) throws JSONException
    {
        String type = m.getJSONArray(SCHEMAS).getString(0).endsWith(GROUP) ? GROUP : USER;
        String display = m.optString("userName", null);
        if (display == null) {
            display = m.optString("displayName", "");
        }
        return new JSONObject(Map.of(
            VALUE, m.opt(ID),
            "type", type,
            "$ref", "/xwiki/rest/scim/v2/" + type + "s/" + m.get(ID),
            "display", display)
        );
    }

    private static JSONObject getResource(String resourceType, String displayName, String externalId)
        throws JSONException
    {
        return new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String[] {SCIM_SCHEMA_PREFIX + resourceType}),
            "displayName", displayName,
            "externalId", externalId,
            "meta", new JSONObject(Map.of(
                "resourceType", resourceType,
                "created", "2010-01-23T04:56:22Z",
                "lastModified", "2011-05-13T04:42:34Z"
            ))
        ));
    }

    private static final String NON_EXISTING_USER_ID = "thisuserdoesnotexxist";
    private static final String SCIM = "SCIM";
    private static final List<String> SCIM_CODE = List.of(SCIM, "Code");
    private static final String CONFIGURATION = "Configuration";
    private static final String XWIKIDOT = "XWiki.";

    private URI baseScimURL;

    @BeforeAll
    void setup(TestUtils setup) throws URISyntaxException
    {
        baseScimURL = new URI(getScimEndpointURL(setup).replace("http://", "http://ScimAdmin:scimPassword@"));
        createUser(setup, SCIM_ADMIN);
        createUser(setup, SCIM_ADMIN2);
        setScimAdmins(setup, "");
        setup.deletePage(XWIKI_SPACE, NEWUSER);
        setup.deletePage(XWIKI_SPACE, GROUP1);
        setup.deletePage(XWIKI_SPACE, SUB_GROUP);
        setup.deletePage(XWIKI_SPACE, USER);
        setup.deletePage(XWIKI_SPACE, USER_1);
        setup.deletePage(XWIKI_SPACE, USER_2);
        setup.deletePage(XWIKI_SPACE, USER_3);
        setup.deletePage(XWIKI_SPACE, USER_4);
    }

    private static void createUser(TestUtils setup, XWikiUser user)
    {
        setup.createUser(user.userName(), user.password(), null);
    }

    private static JSONObject clone(JSONObject o) throws JSONException
    {
        JSONObject r = new JSONObject();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            r.put(key, o.get(key));
        }

        return r;
    }

    private static void sortArray(JSONObject o, String k, Comparator<JSONObject> c) throws JSONException
    {
        JSONArray toSort = o.getJSONArray(k);
        List<JSONObject> sorted = new ArrayList<>(toSort.length());
        for (int i = 0; i < toSort.length(); i++) {
            sorted.add(toSort.getJSONObject(i));
        }
        sorted.sort(c);
        o.put(k, new JSONArray(sorted));
    }

    @Test
    @Order(1)
    void testUnauthorized(TestUtils setup) throws Exception
    {
        // We don't add SCIM_ADMIN to SCIM.Code.Configuration
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), SCIM_ADMIN);
        assertUnauthorized(getMethod);
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, NON_EXISTING_USER_ID),
            SCIM_ADMIN);
        assertUnauthorized(deleteMethod);
    }

    @Test
    @Order(2)
    void testUsers(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName() + ',' + XWIKIDOT + SCIM_ADMIN2.userName());

        // We check that the list of SCIM users is empty
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);

        // We create a first user...
        String userId = assertCreateUserOK(setup, NEW_USER, SCIM_ADMIN, NEW_USER.getString("userName"), "1");

        // ... and check that it appears in the list and we can get it
        assertUsersListEquals(setup, List.of(NEW_USER), SCIM_ADMIN);
        assertGetUserOK(setup, userId, SCIM_ADMIN, NEW_USER);
        assertTrue(setup.rest().exists(NEWUSER_REF));

        // ... only for the SCIM admin we used
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);
        assertGetUserNotFound(setup, userId, SCIM_ADMIN2);
        assertCorrectRightsOnResourceDocument(setup, userId, USER);

        // Deleting a non-existing user triggers 404
        assertDeleteUserNotFound(setup, "thisuserdoesnotexist", SCIM_ADMIN);

        // Trying to delete a user from another SCIM admin fails
        assertDeleteUserNotFound(setup, userId, SCIM_ADMIN2);

        // Trying to delete a user from the same SCIM admin succeeds
        assertDeleteUserOK(setup, userId, SCIM_ADMIN);
        assertFalse(setup.rest().exists(NEWUSER_REF));

        // And the list of users is then of course empty
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
    }

    private void assertCorrectRightsOnResourceDocument(TestUtils setup, String resourceId, String type) throws Exception
    {
        String userResourceURL = setup.getURL(new DocumentReference(
            setup.getCurrentWiki(), List.of(SCIM, RESOURCES, SCIM_ADMIN.userName(), type), resourceId));
        // For some reason, the host in the rest base URL works and not the global baseURL, so we use that instead.
        userResourceURL = Strings.CS.removeEnd(setup.rest().getBaseURL(), "rest")
                + Strings.CS.removeStart(userResourceURL, setup.getBaseURL());
        GetMethod getAsAdmin = executeGet(userResourceURL, SCIM_ADMIN);
        assertOK(getAsAdmin);
        GetMethod getAsAdmin2 = executeGet(userResourceURL, SCIM_ADMIN2);
        assertForbidden(getAsAdmin2);
    }

    @Test
    @Order(3)
    void testGroups(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName() + ',' + XWIKIDOT + SCIM_ADMIN2.userName());

        // Deleting a non-existing group triggers 404
        assertDeleteGroupNotFound(setup, "thisgroupdoesnotexist", SCIM_ADMIN);

        Group1Members objs = createAndAssertGroup1(setup);

        // Trying to delete a user from another SCIM admin fails
        String group1Id = objs.group1().getString(ID);
        assertDeleteGroupNotFound(setup, group1Id, SCIM_ADMIN2);

        // Trying to delete a user from the same SCIM admin succeeds
        assertDeleteGroupOK(setup, objs.subgroup().getString(ID), SCIM_ADMIN);
        assertFalse(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));

        // We just removed subgroup, so it's not a member of group1 anymore
        removeGroupMembers(objs.group1(), List.of(objs.subgroup().getString(ID)));

        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, objs.group1(), objs.group1().getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, GROUP1)));
        assertCorrectRightsOnResourceDocument(setup, group1Id, GROUP);

        assertDeleteGroupOK(setup, group1Id, SCIM_ADMIN);
        assertDeleteUserOK(setup, objs.user1().getString(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup, objs.user2().getString(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup, objs.user3().getString(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup, objs.user4().getString(ID), SCIM_ADMIN);

        // And the list of users groups is then of course empty
        assertGroupsListIsEmpty(setup, SCIM_ADMIN);
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
    }

    private Group1Members createAndAssertGroup1(TestUtils setup) throws Exception
    {
        // We check that the list of SCIM users is empty
        assertGroupsListIsEmpty(setup, SCIM_ADMIN);
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);

        JSONObject user1 = getUser(USER_1, "User 1", "externalIdOfUser1");
        JSONObject user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        JSONObject user3 = getUser(USER_3, "User 3", "externalIdOfUser3");
        JSONObject user4 = getUser(USER_4, "User 4", "externalIdOfUser4");

        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN, user1.getString("userName"), "1");
        String user2Id = assertCreateUserOK(setup, user2, SCIM_ADMIN, user2.getString("userName"), "1");
        String user3Id = assertCreateUserOK(setup, user3, SCIM_ADMIN, user3.getString("userName"), "1");
        String user4Id = assertCreateUserOK(setup, user4, SCIM_ADMIN, user4.getString("userName"), "1");

        user1.put(ID, user1Id);
        user2.put(ID, user2Id);
        user3.put(ID, user3Id);
        user4.put(ID, user4Id);

        JSONObject subgroup = getGroup(SUB_GROUP, "subgroupExternalId", List.of(user3, user4));
        String subgroupId = assertCreateGroupOK(setup, subgroup, SCIM_ADMIN);
        subgroup.put(ID, subgroupId);
        assertGetGroupOK(setup, subgroupId, SCIM_ADMIN, subgroup, subgroup.getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));
        JSONObject group1 = getGroup(GROUP1, "group1externalId",
            List.of(user1, user2, user3, subgroup));
        String group1Id = assertCreateGroupOK(setup, group1, SCIM_ADMIN);
        group1.put(ID, group1Id);
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, group1, group1.getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, GROUP1)));
        return new Group1Members(user1, user2, user3, user4, subgroup, group1);
    }

    @Test
    @Order(4)
    void testGroupPatch(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName() + ',' + XWIKIDOT + SCIM_ADMIN2.userName());
        Group1Members objs = createAndAssertGroup1(setup);
        String group1Id = (String) objs.group1().get(ID);
        String group1URL = getScimGroupsEndpointURL(setup) + "/" + group1Id;
        PatchMethod patchMethod = executePatch(group1URL, SCIM_ADMIN, new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String[] {SCIM_SCHEMA_PREFIX + "PatchOp"}),
            "Operations", new JSONArray(new JSONObject [] {
                new JSONObject(Map.of(
                    "op", "remove",
                    "path", "members["
                        + "value eq " + new JsonMapper().writeValueAsString(objs.user1().get(ID))
                        + " or "
                        + "display eq \"user2\""
                        + "]"
                )),
                new JSONObject(Map.of(
                    "op", "remove",
                    "path", "members["
                        + "value eq " + new JsonMapper().writeValueAsString(objs.subgroup().get(ID))
                        + "]"
                ))
            })
        )));
        assertOK(patchMethod);
        JSONObject patchedGroup1 = clone(objs.group1());
        removeGroupMembers(patchedGroup1, List.of(
            (String) objs.subgroup().get(ID),
            (String) objs.user1().get(ID),
            (String) objs.user2().get(ID)
        ));
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, patchedGroup1.getString("displayName"), "2");

        JSONObject user2Member = getMember(objs.user2());
        patchMethod = executePatch(group1URL, SCIM_ADMIN, new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String[] {SCIM_SCHEMA_PREFIX + "PatchOp"}),
            "Operations", new JSONArray(new JSONObject[] {
                new JSONObject(Map.of(
                    "op", "add",
                    "path", MEMBERS,
                    "value", new JSONArray(new JSONObject[] {user2Member})
                ))
            })))
        );
        assertOK(patchMethod);
        patchedGroup1.getJSONArray(MEMBERS).put(user2Member);
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, patchedGroup1.getString("displayName"), "3");

        JSONObject user1Member = getMember(objs.user1());
        JSONObject user3Member = getMember(objs.user3());

        patchMethod = executePatch(group1URL, SCIM_ADMIN, new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String[] {SCIM_SCHEMA_PREFIX + "PatchOp"}),
            "Operations", new JSONObject[] {
                new JSONObject(Map.of(
                    "op", "replace",
                    "path", MEMBERS,
                    "value", new JSONArray(new JSONObject[] { user1Member, user3Member })
                ))
            }
        )));
        assertOK(patchMethod);
        patchedGroup1.put(MEMBERS, new JSONArray(new JSONObject[] {user1Member, user3Member}));
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, patchedGroup1.getString("displayName"), "4");

        patchMethod = executePatch(group1URL, SCIM_ADMIN, new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String [] {SCIM_SCHEMA_PREFIX + "PatchOp"}),
            "Operations", new JSONArray(new JSONObject[] {
                new JSONObject(Map.of(
                    "op", "remove",
                    "path", MEMBERS
                ))
            })
        )));
        assertOK(patchMethod);
        patchedGroup1.put(MEMBERS, new JSONArray());
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, patchedGroup1.getString("displayName"), "5");

        assertDeleteGroupOK(setup, (String) objs.subgroup().get(ID), SCIM_ADMIN);
        assertDeleteGroupOK(setup, (String) objs.group1().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user1().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user2().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user3().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user4().get(ID), SCIM_ADMIN);
    }

    @Test
    @Order(5)
    void testSelf(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "Me", SCIM_ADMIN);
        assertOK(getMethod);
        JSONObject actual = (JSONObject) getJSONResponse(getMethod);
        JSONObject meta = actual.getJSONObject("meta");
        assertNotNull(meta.remove("created"));
        assertNotNull(meta.remove("lastModified"));
        JSONObject expected = new JSONObject(Map.of(
            "meta", new JSONObject(Map.of(
                "resourceType", "User",
                "location", getScimUsersEndpointURL(setup) + "/me",
                "version", "1.1"
            )),
            "id", "me",
            "active", true,
            "displayName", "",
            "userName", "ScimAdmin",
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:schemas:core:2.0:User"})
        ));
        Iterator<String> keys = actual.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object v = actual.get(key);
            if (v == null || (v instanceof JSONArray a && a.length() == 0)) {
                keys.remove();
            }
        }
        JSONAssert.assertEquals(expected, actual, true);
    }

    @Test
    @Order(6)
    void testResourceTypes(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        JSONObject expectedGroupResource = getResourceType(setup, "Group");
        JSONObject expectedUserResource = getResourceType(setup, "User");

        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes", SCIM_ADMIN);
        JSONArray expectedResources = new JSONArray(new JSONObject[] {expectedGroupResource, expectedUserResource});
        JSONObject expected = new JSONObject(Map.of(
            "totalResults", 2,
            "startIndex", 1,
            "itemsPerPage", 2,
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:api:messages:2.0:ListResponse"}),
            RESOURCES, expectedResources
        ));
        JSONObject actual = getJSONResponse(getMethod);
        sortArray(actual, RESOURCES, COMPARING_WITH_ID);
        JSONAssert.assertEquals(expected, actual, true);

        getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes/Group", SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        JSONAssert.assertEquals(expectedGroupResource, actual, true);

        getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes/User", SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        JSONAssert.assertEquals(expectedUserResource, actual, true);
    }

    @Test
    @Order(7)
    void testSchemas(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "Schemas", SCIM_ADMIN);

        JSONObject expected = new JSONObject(Map.of(
            "totalResults",	2,
            "startIndex",	1,
            "itemsPerPage",	2,
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:api:messages:2.0:ListResponse"})
        ));

        JSONObject actual = getJSONResponse(getMethod);

        sortArray(actual, RESOURCES, COMPARING_WITH_ID);
        JSONArray actualResources = (JSONArray) actual.remove(RESOURCES);
        assertNotNull(actualResources);
        assertEquals(2, actualResources.length());
        JSONAssert.assertEquals(expected, actual, true);

        JSONObject groupSchema = actualResources.getJSONObject(0);
        assertSchemaOK(setup, "Group", groupSchema);

        JSONObject userSchema = actualResources.getJSONObject(1);
        assertSchemaOK(setup, "User", userSchema);

        getMethod = executeGet(getScimEndpointURL(setup) + "Schemas/" + groupSchema.get("id"), SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        assertSchemaOK(setup, "Group", actual);

        getMethod = executeGet(getScimEndpointURL(setup) + "Schemas/" + userSchema.get("id"), SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        assertSchemaOK(setup, "User", actual);
    }

    // TODO test /Search
    // TODO test /Bulk

    @Test
    @Order(8)
    void testPut(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());

        JSONObject user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN, user1.getString("userName"), "1");
        user1.put(ID, user1Id);

        JSONObject user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        String user2Id = assertCreateUserOK(setup, user2, SCIM_ADMIN, user2.getString("userName"), "1");
        user2.put(ID, user2Id);

        JSONObject group1 = getGroup(GROUP1, "group1externalId", List.of(user1));
        String group1Id = assertCreateGroupOK(setup, group1, SCIM_ADMIN);
        JSONObject updatedUser = clone(user1);
        updatedUser.put("displayName", "Renamed User");
        updatedUser.put("externalId", "renamedUserExternalId");
        updatedUser.put("phoneNumbers", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "+33123456789",
                "type", "work"
            ))
        }));

        assertPutUserOK(setup, user1Id, SCIM_ADMIN, updatedUser, updatedUser.getString("userName"), "2");
        JSONObject updatedGroup = getGroup(GROUP1, "renamedGroupExternalId", List.of(user2));
        assertPutGroupOK(setup, group1Id, SCIM_ADMIN, updatedGroup, updatedGroup.getString("displayName"), "2");
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, updatedGroup, updatedGroup.getString("displayName"), "2");

        updatedUser.put("userName", "renamedUser");

        // Renaming a resource fails
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + user1Id, SCIM_ADMIN, updatedUser);
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, putMethod.getStatusCode(), getHttpMethodInfo(putMethod));

        assertDeleteGroupOK(setup, group1Id, SCIM_ADMIN);
        assertDeleteUserOK(setup, user1Id, SCIM_ADMIN);
        assertDeleteUserOK(setup, user2Id, SCIM_ADMIN);
    }

    @Test
    @Order(9)
    void testUserPatch(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName() + ',' + XWIKIDOT + SCIM_ADMIN2.userName());
        JSONObject user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN, user1.getString("userName"), "1");
        user1.put(ID, user1Id);

        String user1URL = getScimUsersEndpointURL(setup) + "/" + user1Id;
        PatchMethod patchMethod = executePatch(user1URL, SCIM_ADMIN, new JSONObject(Map.of(
            SCHEMAS, new JSONArray(new String[] { SCIM_SCHEMA_PREFIX + "PatchOp" }),
            "Operations", new JSONArray(new JSONObject[] {
                new JSONObject(Map.of(
                    "op", "add",
                    "path", "name.familyName",
                    "value", "New Family Name"
                )),
                new JSONObject(Map.of(
                    "op", "remove",
                    "path", "ims[type eq \"aim\"]"
                )),
                new JSONObject(Map.of(
                    "op", "add",
                    "path", "active",
                    "value", false
                ))
            })
        )));
        assertOK(patchMethod);
        user1.remove("ims");
        user1.put("active", false);
        JSONObject newName = user1.getJSONObject("name");
        newName.put("familyName", "New Family Name");
        user1.put("name", newName);
        assertGetUserOK(setup, user1Id, SCIM_ADMIN, user1, "2");
        assertDeleteUserOK(setup, user1Id, SCIM_ADMIN);
    }

    @Test
    @Order(10)
    void testServiceProviderConfig(TestUtils setup) throws Exception
    {
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ServiceProviderConfig", SCIM_ADMIN);
        assertOK(getMethod);
        JSONObject actual = getJSONResponse(getMethod);
        JSONArray authenticationSchemes = actual.getJSONArray("authenticationSchemes");
        assertNotNull(authenticationSchemes);
        Set<String> types = new HashSet<>(authenticationSchemes.length());
        for (int i = 0; i < authenticationSchemes.length(); i++) {
            types.add(authenticationSchemes.getJSONObject(i).getString("type"));
        }
        assertEquals(Set.of("oauth2", "httpbasic"), types);
    }

    @Test
    @Order(11)
    void testCreateUserWithOIDCEnabledDefaultSubject(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        String id = assertCreateUserOK(setup, NEW_USER, SCIM_ADMIN, NEW_USER.getString("userName"), "1");
        String userName = NEW_USER.getString("userName");
        DocumentReference userRef = new DocumentReference(XWIKI_WIKI, "XWiki", userName);
        Map<String, String> properties = getOIDCProperties(setup, userRef);
        assertEquals(OIDC_ISSUER, properties.get("issuer"));
        assertEquals(userName, properties.get("subject"));
        assertDeleteUserOK(setup, id, SCIM_ADMIN);
    }

    @Test
    @Order(12)
    void testCreateUserWithOIDCEnabledCustomSubject(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        setUserNameCleaning(setup, true);
        setOIDC(setup, true,
            "${userName.clean}${externalId.clean}${email.clean}${nickName.clean}${userType.clean}${displayName.clean}");
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        JSONObject user1 = getFullUser1();
        String id = assertCreateUserOK(setup, user1, SCIM_ADMIN, user1.getString("userName"), "1");
        String userName = (String) user1.get("userName");
        DocumentReference userRef = new DocumentReference("xwiki", "XWiki", userName);
        Map<String, String> properties = getOIDCProperties(setup, userRef);
        assertEquals("user1externalIdOfUser1bjensenexamplecomBabsInternBabsJensen", properties.get("subject"));

        // Creating a user already managed with SCIM should still conflict
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), SCIM_ADMIN, user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteUserOK(setup, id, SCIM_ADMIN);
    }

    @Test
    @Order(13)
    void testCreateUserWithExistingOIDCObject(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        JSONObject user1 = getFullUser1();
        user1.put("userName", "not.clean.username");
        String userReferenceName = "totallydifferentname";
        String cleaned = "notcleanusername";
        setup.updateObject("XWiki", userReferenceName, "XWiki.OIDC.UserClass", 0, "subject", cleaned, "issuer", OIDC_ISSUER);
        String id = assertCreateUserOK(setup, user1, SCIM_ADMIN, cleaned, "1");

        org.xwiki.rest.model.jaxb.Object object = setup.rest().get(
            new ObjectReference("SCIM.Code.SCIMResourceClass[0]",
                new DocumentReference("xwiki", List.of(SCIM, RESOURCES, SCIM_ADMIN.userName(), USER), id)));

        assertEquals("XWiki." + cleaned, getProperties(object).get("reference"));

        // Creating a user already managed with SCIM should still conflict
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), SCIM_ADMIN, user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteUserOK(setup, id, SCIM_ADMIN);
    }

    @Test
    @Order(14)
    void testConflictingUserCreationWithOIDCEnabled(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, SCIM_ADMIN);
        JSONObject user1 = getFullUser1();
        user1.put("userName", "not.clean.username");
        String cleaned = "notcleanusername";
        DocumentReference userRef = new DocumentReference("xwiki", "XWiki", cleaned);
        setup.createPage(userRef, "");
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), SCIM_ADMIN, user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));
        setup.deletePage(userRef);
    }

    @Test
    @Order(15)
    void testCreateExistingGroupWithOIDCEnabled(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        setUserNameCleaning(setup, true);
        setOIDC(setup, false, "");
        assertGroupsListIsEmpty(setup, SCIM_ADMIN);
        JSONObject group1 = getGroup(GROUP1, "group1externalId", List.of());
        setup.createPage("XWiki", GROUP1, "", "");

        // creating an already existing group fails with oidc disabled
        PostMethod postMethod = executePost(getScimGroupsEndpointURL(setup), SCIM_ADMIN, group1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        // but succeeds with oidc enabled
        setOIDC(setup, true, "");
        String group1Id = assertCreateGroupOK(setup, group1, SCIM_ADMIN);

        // unless the group is already managed with SCIM
        postMethod = executePost(getScimGroupsEndpointURL(setup), SCIM_ADMIN, group1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteGroupOK(setup, group1Id, SCIM_ADMIN);
    }

    private static Map<String, String> getOIDCProperties(TestUtils setup, DocumentReference userRef)
        throws Exception
    {
        org.xwiki.rest.model.jaxb.Object object = setup.rest().get(
            new ObjectReference("XWiki.OIDC.UserClass[0]", userRef));
        assertNotNull(object);
        return getProperties(object);
    }

    private static Map<String, String> getProperties(org.xwiki.rest.model.jaxb.Object object)
    {
        List<Property> propertyList = object.getProperties();
        Map<String, String> properties = new HashMap<>(propertyList.size());
        for (Property p : propertyList) {
            properties.put(p.getName(), p.getValue());
        }
        return properties;
    }

    private static JSONObject getFullUser1() throws JSONException
    {
        JSONObject user1 = getUser(USER_1, "Babs Jensen", "externalIdOfUser1");
        user1.put("nickName", "Babs");
        user1.put("title", "Tour Guide");
        user1.put("userType", "Intern");
        user1.put("preferredLanguage", "da, en-gb;q=0.8, en;q=0.7");
        user1.put("locale", "fr");
        user1.put("timezone", "America/Los_Angeles");
        user1.put("emails", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "bjensen@example.com",
                "type", "work",
                "primary", true
            )),
            new JSONObject(Map.of(
                "value", "babs@jensen.org",
                "type", "home"
            ))
        }));
        user1.put("phoneNumbers", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "555-555-5555",
                "type", "work"
            )),
            new JSONObject(Map.of(
                "value", "555-555-4444",
                "type", "mobile"
            ))
        }));
        user1.put("ims", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "someaimhandle",
                "type", "aim"
            ))
        }));
        user1.put("photos", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "https://photos.example.com/profilephoto/72930000000Ccne/F",
                "type", "photo"
            )),
            new JSONObject(Map.of(
                "value", "https://photos.example.com/profilephoto/72930000000Ccne/T",
                "type", "thumbnail"
            ))
        }));
        user1.put("addresses", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "type", "work",
                "streetAddress", "100 Universal City Plaza",
                "locality", "Hollywood",
                "region", "CA",
                "postalCode", "91608",
                "country", "USA",
                "formatted", "100 Universal City Plaza\nHollywood, CA 91608 USA",
                "primary", true
            )),
            new JSONObject(Map.of(
                "type", "home",
                "streetAddress", "456 Hollywood Blvd",
                "locality", "Hollywood",
                "region", "CA",
                "postalCode", "91608",
                "country", "USA",
                "formatted", "456 Hollywood Blvd\nHollywood, CA 91608 USA"
            ))
        }));
        user1.put("name", new JSONObject(Map.of(
            "formatted", "Ms. Barbara J Jensen, III",
            "familyName", "Jensen",
            "givenName", "Barbara",
            "middleName", "Jane",
            "honorificPrefix", "Ms.",
            "honorificSuffix", "III"
        )));
        user1.put("profileUrl", "https://login.example.com/bjensen");
        return user1;
    }

    private static JSONObject getResourceType(TestUtils setup, String resourceType) throws JSONException
    {
        return new JSONObject(Map.of(
            "meta", Map.of(
                "resourceType", "ResourceType",
                "location", getScimEndpointURL(setup) + "ResourceTypes/" + resourceType
            ),
            "id", resourceType,
            "name", resourceType,
            "description", "Top level Scim" + resourceType,
            "endpoint", "/" + resourceType + 's',
            "schemaExtensions", new JSONArray(),
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:schemas:core:2.0:ResourceType"}),
            "schema", "urn:ietf:params:scim:schemas:core:2.0:" + resourceType
        ));
    }

    private static void assertSchemaOK(TestUtils setup, String resourceType, JSONObject actual) throws JSONException
    {
        String id = "urn:ietf:params:scim:schemas:core:2.0:" + resourceType;
        JSONObject expectedResourceSchema = new JSONObject(Map.of(
            "meta", Map.of(
                "resourceType", "Schema",
                "location", getScimEndpointURL(setup) + "Schemas/" + id
            ),
            "id", id,
            "name", resourceType,
            "description", "Top level Scim" + resourceType
        ));
        JSONArray attributes = (JSONArray) actual.remove("attributes");
        assertAttributesOK(attributes);

        JSONAssert.assertEquals(expectedResourceSchema, actual, true);
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

    private static void removeGroupMembers(JSONObject group1, List<String> membersToRemove) throws JSONException
    {
        JSONArray members = group1.getJSONArray(MEMBERS);
        int i = 0;
        while (i < members.length()) {
            JSONObject m = members.getJSONObject(i);
            if (membersToRemove.contains(m.getString(VALUE))) {
                members.remove(i);
            } else {
                i++;
            }
        }
    }

    private String assertCreateUserOK(TestUtils setup, JSONObject newUser, XWikiUser scimAdmin, String userName,
        String version) throws Exception
    {
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin, newUser);
        assertOK(postMethod);
        return assertUserEqual(newUser, postMethod, userName, version);
    }

    private void assertPutUserOK(TestUtils setup, String userId, XWikiUser scimAdmin, JSONObject putUser,
        String userName, String version) throws Exception
    {
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + userId, scimAdmin, putUser);
        assertOK(putMethod);
        assertUserEqual(putUser, putMethod, userName, version);
    }

    private void assertPutGroupOK(TestUtils setup, String groupId, XWikiUser scimAdmin, JSONObject putGroup,
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
        JSONObject expected = new JSONObject(Map.of(
            "meta", new JSONObject(Map.of(
                "resourceType", USER,
                "location", "http://localhost:8080/xwiki/rest/scim/v2/Users/" + receivedId,
                "version", version + ".1"
            )),
            ID, receivedId,
            "externalId", userSpec.opt("externalId"),
            "active", active,
            "displayName", userSpec.optString("displayName", ""),
            "userName", userName,
            SCHEMAS, new JSONArray(new String[] {"urn:ietf:params:scim:schemas:core:2.0:User"})
        ));

        expected.put("userType", userSpec.opt("userType"));
        expected.put("profileUrl", userSpec.opt("profileUrl"));
        expected.put("nickName", userSpec.opt("nickName"));

        keepLists(expected, userSpec, "phoneNumbers", "addresses", "ims", "emails", "photos");
        keepSingle(expected, userSpec, "title", "timezone");
        expected.put("name", userSpec.opt("name"));
        keepLocale(expected, userSpec);
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

    private static void keepLocale(JSONObject expected, JSONObject userSpec) throws JSONException
    {
        // simulate the limitations of the current implementation
        String locale = userSpec.optString("preferredLanguage", userSpec.optString("locale", null));
        if (locale != null) {
            String r = Locale.LanguageRange.parse(locale).get(0).getRange();
            expected.put("preferredLanguage", r);
            expected.put("locale", r);
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

    private String assertCreateGroupOK(TestUtils setup, JSONObject newGroup, XWikiUser scimAdmin)
        throws Exception
    {
        PostMethod postMethod = executePost(getScimGroupsEndpointURL(setup), scimAdmin, newGroup);
        assertOK(postMethod);
        return assertGroupEqual(newGroup, postMethod, newGroup.getString("displayName"), "1");
    }

    private void assertDeleteUserOK(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        List<String> referenceSpace = List.of(SCIM, RESOURCES, scimAdmin.userName(), USER);
        assertTrue(setup.pageExists(referenceSpace, id));
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, id), scimAdmin);
        assertOK(deleteMethod);
        assertFalse(setup.pageExists(referenceSpace, id));
    }

    private void assertDeleteGroupOK(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        List<String> referenceSpace = List.of(SCIM, RESOURCES, scimAdmin.userName(), GROUP);
        assertTrue(setup.pageExists(referenceSpace, id));
        DeleteMethod deleteMethod = executeDelete(getSCIMGroupURL(setup, id), scimAdmin);
        assertOK(deleteMethod);
        assertFalse(setup.pageExists(referenceSpace, id));
    }

    private void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject userSpec)
        throws Exception
    {
        assertGetUserOK(setup, id, scimAdmin, userSpec, "1");
    }

    private void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject userSpec,
        String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertUserEqual(userSpec, getMethod, userSpec.getString("userName"), version);
    }

    private void assertGetUserNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertNotFound(getMethod);
    }

    private static String getSCIMUserURL(TestUtils setup, String id)
    {
        return getScimUsersEndpointURL(setup) + "/" + id;
    }

    private void assertGetGroupOK(TestUtils setup, String id, XWikiUser scimAdmin, JSONObject groupSpec,
        String displayName, String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMGroupURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertGroupEqual(groupSpec, getMethod, displayName, version);
    }

    private static String getSCIMGroupURL(TestUtils setup, String id)
    {
        return getScimGroupsEndpointURL(setup) + "/" + id;
    }

    private void assertDeleteUserNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, id), scimAdmin);
        assertNotFound(deleteMethod);
    }

    private void assertDeleteGroupNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        DeleteMethod deleteMethod = executeDelete(getSCIMGroupURL(setup, id), scimAdmin);
        assertNotFound(deleteMethod);
    }

    private static void assertNotFound(HttpMethodBase method)
    {
        assertEquals(404, method.getStatusCode());
    }

    private void assertUsersListIsEmpty(TestUtils setup, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        assertZeroResults(getMethod);
    }

    private void assertGroupsListIsEmpty(TestUtils setup, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getScimGroupsEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        assertZeroResults(getMethod);
    }

    private void assertUsersListEquals(TestUtils setup, List<JSONObject> expectedUsers, XWikiUser scimAdmin)
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

    private static void setScimAdmins(TestUtils setup, String users)
    {
        setup.updateObject(SCIM_CODE, CONFIGURATION, "SCIM.Code.ConfigurationClass", 0,
            "users", users, "advertisedAuthSchemes", "oauth2,httpbasic");
    }

    private static void setOIDC(TestUtils setup, boolean enabled, String subject)
    {
        setup.updateObject(SCIM_CODE, CONFIGURATION, "SCIM.Code.ConfigurationClass", 0,
            "oidcEnabled", enabled, "oidcSubject", subject, "oidcIssuer", OIDC_ISSUER);
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

    /* We import the Apache SCimple compliance tests */

    @Nested
    @UITest
    class ScimpleUsersIT extends org.apache.directory.scim.compliance.tests.UsersIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL;
        }

        @Override
        protected String authorization()
        {
            return AUTHORIZATION;
        }
    }

    @Nested
    @UITest
    class ScimpleGroupsIT extends org.apache.directory.scim.compliance.tests.GroupsIT
    {
        @BeforeAll
        void setup(TestUtils setup)
        {
            setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
            setUserNameCleaning(setup, false);
        }

        @Override
        protected URI uri()
        {
            return baseScimURL;
        }

        @Override
        protected String authorization()
        {
            return AUTHORIZATION;
        }
    }

    private static void setUserNameCleaning(TestUtils setup, boolean enabled)
    {
        setup.updateObject(SCIM_CODE, CONFIGURATION, "SCIM.Code.ConfigurationClass", 0,
            "usernameCleaningEnabled", enabled ? 1 : 0);
    }

    @Nested
    @UITest
    class ScimpleSchemasIT extends org.apache.directory.scim.compliance.tests.SchemasIT
    {
        @BeforeAll
        void setup(TestUtils setup)
        {
            setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
            setUserNameCleaning(setup, false);
        }

        @Override
        protected URI uri()
        {
            return baseScimURL;
        }

        @Override
        protected String authorization()
        {
            return AUTHORIZATION;
        }
    }

    @Nested
    @UITest
    class ScimpleResourcesTypesIT extends org.apache.directory.scim.compliance.tests.ResourceTypesIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL;
        }

        @Override
        protected String authorization()
        {
            return AUTHORIZATION;
        }
    }

    @Nested
    @UITest
    class ScimpleServiceProviderConfigIT extends org.apache.directory.scim.compliance.tests.ServiceProviderConfigIT
    {
        @Override
        protected URI uri()
        {
            return baseScimURL;
        }

        @Override
        protected String authorization()
        {
            return AUTHORIZATION;
        }
    }
}
