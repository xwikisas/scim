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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.directory.scim.spec.extension.EnterpriseExtension;
import org.apache.directory.scim.spec.resources.ScimGroup;
import org.apache.directory.scim.spec.resources.ScimUser;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static com.xwiki.scim.rest.Constants.NEWUSER;
import static com.xwiki.scim.rest.Constants.SCIM_ADMIN2;
import static com.xwiki.scim.rest.Constants.GROUP1;
import static com.xwiki.scim.rest.Constants.SUB_GROUP;
import static com.xwiki.scim.rest.Constants.USER;
import static com.xwiki.scim.rest.Constants.USER_1;
import static com.xwiki.scim.rest.Constants.USER_2;
import static com.xwiki.scim.rest.Constants.USER_3;
import static com.xwiki.scim.rest.Constants.USER_4;

import static com.xwiki.scim.rest.Utils.setScimAdmins;
import static com.xwiki.scim.rest.Utils.setUserNameCleaning;
import static com.xwiki.scim.rest.Utils.getScimEndpointURL;
import static com.xwiki.scim.rest.Utils.getJSONResponse;
import static com.xwiki.scim.rest.Utils.getScimUsersEndpointURL;
import static com.xwiki.scim.rest.Utils.getScimGroupsEndpointURL;

@UITest(vnc = false, debug = false)
//@UITest(vnc = false, debug = true, servletEngine = org.xwiki.test.docker.junit5.servletengine.ServletEngine.EXTERNAL)
class ScimRestIT extends AbstractScimHttp
{
    private static final int EXPECTED_SCHEMA_COUNT = 3;
    private static final JSONObject NEW_USER = getUser(NEWUSER, "New User", "newUserExternalId");
    private static final DocumentReference NEWUSER_REF = new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, NEWUSER);
    private static final String NON_EXISTING_USER_ID = "thisuserdoesnotexxist";

    private static final Comparator<JSONObject> COMPARING_WITH_ID = Comparator.comparing(m -> {
        try {
            return m.getString(ID);
        } catch (JSONException e) {
            fail(e);
            return "";
        }
    });

    private static final DocumentReference SCIM_RESOURCES_WEBHOME =
            new DocumentReference("xwiki", List.of("SCIM", "Resources"), "WebHome");

    @BeforeEach
    void setup(TestUtils setup)
    {
        setup.loginAsSuperAdmin();
        deletePage(setup, NEWUSER);
        deletePage(setup, GROUP1);
        deletePage(setup, SUB_GROUP);
        deletePage(setup, USER);
        deletePage(setup, USER_1);
        deletePage(setup, USER_2);
        deletePage(setup, USER_3);
        deletePage(setup, USER_4);
        deletePage(setup, SCIM_ADMIN2.userName());
        createUser(setup, SCIM_ADMIN2);
        setup.createPage(SCIM_RESOURCES_WEBHOME, "");
        // Replace the following with setup.deleteSpace(SCIM_RESOURCES_WEBHOME.getParent()) when XWIKI-24756 if fixed
        setup.getDriver().get(setup.getURL(SCIM_RESOURCES_WEBHOME,"delete",
                "confirm=1&async=false&affectChildren=on"));
        // setup.executePost(setup.getURL(SCIM_RESOURCES_WEBHOME.getParent(), "delete", "confirm=1&async=false&affectChildren=on"), InputStream.nullInputStream(), "")
        setScimAdmins(setup, scimAdmin());
    }

    @Test
    @Order(1)
    void testUnauthorized(TestUtils setup) throws Exception
    {
        setScimAdmins(setup);
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), scimAdmin());
        assertUnauthorized(getMethod);
        DeleteMethod deleteMethod = executeDelete(getSCIMUserURL(setup, NON_EXISTING_USER_ID), scimAdmin());
        assertUnauthorized(deleteMethod);
    }

    @Test
    @Order(2)
    void testUsers(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, scimAdmin(), SCIM_ADMIN2);

        // We check that the list of SCIM users is empty
        assertUsersListIsEmpty(setup, scimAdmin());
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);

        // We create a first user...
        String userId = assertCreateUserOK(setup, NEW_USER, scimAdmin(), NEW_USER.getString("userName"));

        // ... and check that it appears in the list and we can get it
        assertUsersListEquals(setup, List.of(NEW_USER), scimAdmin());
        assertGetUserOK(setup, userId, scimAdmin(), NEW_USER, "1");
        assertTrue(setup.rest().exists(NEWUSER_REF));

        // ... only for the SCIM admin we used
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);
        assertGetUserNotFound(setup, userId, SCIM_ADMIN2);
        assertCorrectRightsOnResourceDocument(setup, userId, USER);

        // Deleting a non-existing user triggers 404
        assertDeleteUserNotFound(setup, "thisuserdoesnotexist", scimAdmin());

        // Trying to delete a user from another SCIM admin fails
        assertDeleteUserNotFound(setup, userId, SCIM_ADMIN2);

        // Trying to delete a user from the same SCIM admin succeeds
        assertDeleteUserOK(setup, userId, scimAdmin());
        assertFalse(setup.rest().exists(NEWUSER_REF));

        // And the list of users is then of course empty
        assertUsersListIsEmpty(setup, scimAdmin());
    }

    @Test
    @Order(3)
    void testGroups(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, scimAdmin(), SCIM_ADMIN2);

        // Deleting a non-existing group triggers 404
        assertDeleteGroupNotFound(setup, "thisgroupdoesnotexist", scimAdmin());

        Group1Members objs = createAndAssertGroup1(setup);

        // Trying to delete a user from another SCIM admin fails
        String group1Id = objs.group1().getString(ID);
        assertDeleteGroupNotFound(setup, group1Id, SCIM_ADMIN2);

        // Trying to delete a user from the same SCIM admin succeeds
        assertDeleteGroupOK(setup, objs.subgroup().getString(ID), scimAdmin());
        assertFalse(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));

        // We just removed subgroup, so it's not a member of group1 anymore
        removeGroupMembers(objs.group1(), List.of(objs.subgroup().getString(ID)));

        assertGetGroupOK(setup, group1Id, scimAdmin(), objs.group1(), objs.group1().getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, GROUP1)));
        assertCorrectRightsOnResourceDocument(setup, group1Id, GROUP);

        assertDeleteGroupOK(setup, group1Id, scimAdmin());
        assertDeleteUserOK(setup, objs.user1().getString(ID), scimAdmin());
        assertDeleteUserOK(setup, objs.user2().getString(ID), scimAdmin());
        assertDeleteUserOK(setup, objs.user3().getString(ID), scimAdmin());
        assertDeleteUserOK(setup, objs.user4().getString(ID), scimAdmin());

        // And the list of users groups is then of course empty
        assertGroupsListIsEmpty(setup, scimAdmin());
        assertUsersListIsEmpty(setup, scimAdmin());
    }

    private Group1Members createAndAssertGroup1(TestUtils setup) throws Exception
    {
        // We check that the list of SCIM users is empty
        assertGroupsListIsEmpty(setup, scimAdmin());
        assertUsersListIsEmpty(setup, scimAdmin());
        assertUsersListIsEmpty(setup, SCIM_ADMIN2);

        JSONObject user1 = getUser(USER_1, "User 1", "externalIdOfUser1");
        JSONObject user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        JSONObject user3 = getUser(USER_3, "User 3", "externalIdOfUser3");
        JSONObject user4 = getUser(USER_4, "User 4", "externalIdOfUser4");

        String user1Id = assertCreateUserOK(setup, user1, scimAdmin(), user1.getString("userName"));
        String user2Id = assertCreateUserOK(setup, user2, scimAdmin(), user2.getString("userName"));
        String user3Id = assertCreateUserOK(setup, user3, scimAdmin(), user3.getString("userName"));
        String user4Id = assertCreateUserOK(setup, user4, scimAdmin(), user4.getString("userName"));

        user1.put(ID, user1Id);
        user2.put(ID, user2Id);
        user3.put(ID, user3Id);
        user4.put(ID, user4Id);

        JSONObject subgroup = getGroup(SUB_GROUP, "subgroupExternalId", List.of(user3, user4));
        String subgroupId = assertCreateGroupOK(setup, subgroup, scimAdmin());
        subgroup.put(ID, subgroupId);
        assertGetGroupOK(setup, subgroupId, scimAdmin(), subgroup, subgroup.getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));
        JSONObject group1 = getGroup(GROUP1, "group1externalId",
            List.of(user1, user2, user3, subgroup));
        String group1Id = assertCreateGroupOK(setup, group1, scimAdmin());
        group1.put(ID, group1Id);
        assertGetGroupOK(setup, group1Id, scimAdmin(), group1, group1.getString("displayName"), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, GROUP1)));
        return new Group1Members(user1, user2, user3, user4, subgroup, group1);
    }

    @Test
    @Order(4)
    void testGroupPatch(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, scimAdmin(), SCIM_ADMIN2);
        Group1Members objs = createAndAssertGroup1(setup);
        String group1Id = (String) objs.group1().get(ID);
        String group1URL = getScimGroupsEndpointURL(setup) + "/" + group1Id;
        PatchMethod patchMethod = executePatch(group1URL, scimAdmin(), new JSONObject(Map.of(
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
        assertGetGroupOK(setup, group1Id, scimAdmin(), patchedGroup1, patchedGroup1.getString("displayName"), "2");

        JSONObject user2Member = getMember(objs.user2());
        patchMethod = executePatch(group1URL, scimAdmin(), new JSONObject(Map.of(
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
        assertGetGroupOK(setup, group1Id, scimAdmin(), patchedGroup1, patchedGroup1.getString("displayName"), "3");

        JSONObject user1Member = getMember(objs.user1());
        JSONObject user3Member = getMember(objs.user3());

        patchMethod = executePatch(group1URL, scimAdmin(), new JSONObject(Map.of(
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
        assertGetGroupOK(setup, group1Id, scimAdmin(), patchedGroup1, patchedGroup1.getString("displayName"), "4");

        patchMethod = executePatch(group1URL, scimAdmin(), new JSONObject(Map.of(
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
        assertGetGroupOK(setup, group1Id, scimAdmin(), patchedGroup1, patchedGroup1.getString("displayName"), "5");

        assertDeleteGroupOK(setup, (String) objs.subgroup().get(ID), scimAdmin());
        assertDeleteGroupOK(setup, (String) objs.group1().get(ID), scimAdmin());
        assertDeleteUserOK(setup,  (String) objs.user1().get(ID), scimAdmin());
        assertDeleteUserOK(setup,  (String) objs.user2().get(ID), scimAdmin());
        assertDeleteUserOK(setup,  (String) objs.user3().get(ID), scimAdmin());
        assertDeleteUserOK(setup,  (String) objs.user4().get(ID), scimAdmin());
    }

    @Test
    @Order(5)
    void testSelf(TestUtils setup) throws Exception
    {
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "Me", scimAdmin());
        assertOK(getMethod);
        JSONObject actual = getJSONResponse(getMethod);
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
            "userName", scimAdmin().userName(),
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
        JSONObject expectedGroupRT = withResourceTypeFields(getExpectedResourceSchema(setup, "Group"));
        JSONObject expectedUserRT = withResourceTypeFields(getExpectedResourceSchema(setup, "User"));

        JSONArray expectedRTs = new JSONArray(new JSONObject[] {
                expectedGroupRT,
                expectedUserRT
        });

        JSONObject expected = newJSONObject(
            "totalResults", 2,
            "startIndex", 1,
            "itemsPerPage", 2,
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:api:messages:2.0:ListResponse"}),
            RESOURCES, expectedRTs
        );
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes", scimAdmin());
        JSONObject actual = getJSONResponse(getMethod);
        sortArray(actual, RESOURCES, COMPARING_WITH_ID);
        JSONAssert.assertEquals(expected, actual, true);

        for (int i = 0; i < expectedRTs.length(); i++) {
            JSONObject expectedResource = expectedRTs.getJSONObject(i);
            getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes/" + expectedResource.get("name"), scimAdmin());
            actual = getJSONResponse(getMethod);
            JSONAssert.assertEquals(expectedResource, actual, true);
        }
    }

    @Test
    @Order(7)
    void testSchemas(TestUtils setup) throws Exception
    {
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "Schemas", scimAdmin());
        JSONObject expected = new JSONObject(Map.of(
            "totalResults",	EXPECTED_SCHEMA_COUNT,
            "startIndex",	1,
            "itemsPerPage",	EXPECTED_SCHEMA_COUNT,
            "schemas", new JSONArray(new String[] {"urn:ietf:params:scim:api:messages:2.0:ListResponse"})
        ));

        JSONObject actual = getJSONResponse(getMethod);

        sortArray(actual, RESOURCES, COMPARING_WITH_ID);
        JSONArray actualResources = (JSONArray) actual.remove(RESOURCES);
        assertNotNull(actualResources);
        assertEquals(EXPECTED_SCHEMA_COUNT, actualResources.length());
        JSONAssert.assertEquals(expected, actual, true);
        assertSchemas(
                setup,
                actualResources.getJSONObject(0),
                actualResources.getJSONObject(1),
                actualResources.getJSONObject(2)
        );

        getMethod = executeGet(getScimEndpointURL(setup) + "Schemas/" + ScimGroup.SCHEMA_URI, scimAdmin());
        JSONObject actualGroupSchema = getJSONResponse(getMethod);
        getMethod = executeGet(getScimEndpointURL(setup) + "Schemas/" + ScimUser.SCHEMA_URI, scimAdmin());
        JSONObject actualUserSchema = getJSONResponse(getMethod);
        getMethod = executeGet(getScimEndpointURL(setup) + "Schemas/" + EnterpriseExtension.URN, scimAdmin());
        JSONObject actualEnterpriseUserSchema = getJSONResponse(getMethod);
        assertSchemas(setup, actualGroupSchema, actualUserSchema, actualEnterpriseUserSchema);
    }

    private void assertSchemas(TestUtils setup, JSONObject groupSchema, JSONObject userSchema, JSONObject enterpriseUserSchema) throws
            JSONException
    {
        assertResourceSchemaOK(setup, "Group", groupSchema);
        assertResourceSchemaOK(setup, "User", userSchema);
        assertSchemaOK(getExpectedEnterpriseUserSchema(setup), enterpriseUserSchema);
    }

    // TODO test /Search
    // TODO test /Bulk

    @Test
    @Order(8)
    void testPut(TestUtils setup) throws Exception
    {

        JSONObject user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, scimAdmin(), user1.getString("userName"));
        user1.put(ID, user1Id);

        JSONObject user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        String user2Id = assertCreateUserOK(setup, user2, scimAdmin(), user2.getString("userName"));
        user2.put(ID, user2Id);

        JSONObject group1 = getGroup(GROUP1, "group1externalId", List.of(user1));
        String group1Id = assertCreateGroupOK(setup, group1, scimAdmin());
        JSONObject updatedUser = clone(user1);
        updatedUser.put("displayName", "Renamed User");
        updatedUser.put("externalId", "renamedUserExternalId");
        updatedUser.put("phoneNumbers", new JSONArray(new JSONObject[] {
            new JSONObject(Map.of(
                "value", "+33123456789",
                "type", "work"
            ))
        }));

        assertPutUserOK(setup, user1Id, scimAdmin(), updatedUser, updatedUser.getString("userName"), "2");
        JSONObject updatedGroup = getGroup(GROUP1, "renamedGroupExternalId", List.of(user2));
        assertPutGroupOK(setup, group1Id, scimAdmin(), updatedGroup, updatedGroup.getString("displayName"), "2");
        assertGetGroupOK(setup, group1Id, scimAdmin(), updatedGroup, updatedGroup.getString("displayName"), "2");

        updatedUser.put("userName", "renamedUser");

        // Renaming a resource fails
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + user1Id, scimAdmin(), updatedUser);
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, putMethod.getStatusCode(), getHttpMethodInfo(putMethod));

        assertDeleteGroupOK(setup, group1Id, scimAdmin());
        assertDeleteUserOK(setup, user1Id, scimAdmin());
        assertDeleteUserOK(setup, user2Id, scimAdmin());
    }

    @Test
    @Order(9)
    void testUserPatch(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, scimAdmin(), SCIM_ADMIN2);
        JSONObject user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, scimAdmin(), user1.getString("userName"));
        user1.put(ID, user1Id);

        String user1URL = getScimUsersEndpointURL(setup) + "/" + user1Id;
        PatchMethod patchMethod = executePatch(user1URL, scimAdmin(), new JSONObject(Map.of(
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
        assertGetUserOK(setup, user1Id, scimAdmin(), user1, "2");
        assertDeleteUserOK(setup, user1Id, scimAdmin());
    }

    @Test
    @Order(10)
    void testServiceProviderConfig(TestUtils setup) throws Exception
    {
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ServiceProviderConfig", scimAdmin());
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
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, scimAdmin());
        String id = assertCreateUserOK(setup, NEW_USER, scimAdmin(), NEW_USER.getString("userName"));
        String userName = NEW_USER.getString("userName");
        DocumentReference userRef = new DocumentReference(XWIKI_WIKI, "XWiki", userName);
        Map<String, String> properties = getOIDCProperties(setup, userRef);
        assertEquals(OIDC_ISSUER, properties.get("issuer"));
        assertEquals(userName, properties.get("subject"));
        assertDeleteUserOK(setup, id, scimAdmin());
    }

    @Test
    @Order(12)
    void testCreateUserWithOIDCEnabledCustomSubject(TestUtils setup) throws Exception
    {
        setUserNameCleaning(setup, true);
        setOIDC(setup, true,
            "${userName.clean}${externalId.clean}${email.clean}${nickName.clean}${userType.clean}${displayName.clean}");
        assertUsersListIsEmpty(setup, scimAdmin());
        JSONObject user1 = getFullUser1();
        String id = assertCreateUserOK(setup, user1, scimAdmin(), user1.getString("userName"));
        String userName = (String) user1.get("userName");
        DocumentReference userRef = new DocumentReference("xwiki", "XWiki", userName);
        Map<String, String> properties = getOIDCProperties(setup, userRef);
        assertEquals("user1externalIdOfUser1bjensenexamplecomBabsInternBabsJensen", properties.get("subject"));

        // Creating a user already managed with SCIM should still conflict
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin(), user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteUserOK(setup, id, scimAdmin());
    }

    @Test
    @Order(13)
    void testCreateUserWithExistingOIDCObject(TestUtils setup) throws Exception
    {
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, scimAdmin());
        JSONObject user1 = getFullUser1();
        user1.put("userName", "not.clean.username");
        String userReferenceName = "totallydifferentname";
        String cleaned = "notcleanusername";
        setup.updateObject("XWiki", userReferenceName, "XWiki.OIDC.UserClass", 0, "subject", cleaned, "issuer", OIDC_ISSUER);
        String id = assertCreateUserOK(setup, user1, scimAdmin(), cleaned);

        org.xwiki.rest.model.jaxb.Object object = setup.rest().get(
            new ObjectReference("SCIM.Code.SCIMResourceClass[0]",
                new DocumentReference("xwiki", List.of(SCIM, RESOURCES, scimAdmin().userName(), USER), id)));

        assertEquals("XWiki." + cleaned, getProperties(object).get("reference"));

        // Creating a user already managed with SCIM should still conflict
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin(), user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteUserOK(setup, id, scimAdmin());
    }

    @Test
    @Order(14)
    void testConflictingUserCreationWithOIDCEnabled(TestUtils setup) throws Exception
    {
        setUserNameCleaning(setup, true);
        setOIDC(setup, true, "");
        assertUsersListIsEmpty(setup, scimAdmin());
        JSONObject user1 = getFullUser1();
        user1.put("userName", "not.clean.username");
        String cleaned = "notcleanusername";
        DocumentReference userRef = new DocumentReference("xwiki", "XWiki", cleaned);
        setup.createPage(userRef, "");
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin(), user1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));
        setup.deletePage(userRef);
    }

    @Test
    @Order(15)
    void testCreateExistingGroupWithOIDCEnabled(TestUtils setup) throws Exception
    {
        setUserNameCleaning(setup, true);
        setOIDC(setup, false, "");
        assertGroupsListIsEmpty(setup, scimAdmin());
        JSONObject group1 = getGroup(GROUP1, "group1externalId", List.of());
        setup.createPage("XWiki", GROUP1, "", "");

        // creating an already existing group fails with oidc disabled
        PostMethod postMethod = executePost(getScimGroupsEndpointURL(setup), scimAdmin(), group1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        // but succeeds with oidc enabled
        setOIDC(setup, true, "");
        String group1Id = assertCreateGroupOK(setup, group1, scimAdmin());

        // unless the group is already managed with SCIM
        postMethod = executePost(getScimGroupsEndpointURL(setup), scimAdmin(), group1);
        assertEquals(HttpStatus.SC_CONFLICT, postMethod.getStatusCode(), getHttpMethodInfo(postMethod));

        assertDeleteGroupOK(setup, group1Id, scimAdmin());
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
}
