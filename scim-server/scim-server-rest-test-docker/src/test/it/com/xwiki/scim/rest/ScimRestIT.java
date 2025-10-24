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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.apache.commons.httpclient.methods.GetMethod;

import com.fasterxml.jackson.databind.json.JsonMapper;

import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.test.JSONAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final Map<String, Object> NEW_USER = getUser(NEWUSER, "New User", "newUserExternalId");
    private static final String ID = "id";
    private static final String GROUP = "Group";
    private static final String USER = "User";
    private static final String SCHEMAS = "schemas";
    private static final String VALUE = "value";
    private static final String MEMBERS = "members";
    private static final String SCIM_SCHEMA_PREFIX = "urn:ietf:params:scim:schemas:core:2.0:";

    private static final Set<String> SCHEMA_ATTRIBUTES =
        Set.of("name", "type", "multiValued", "description", "required", "caseExact", "mutability", "returned",
            "uniqueness");

    private  static final Comparator<Map<String, Object>> COMPARING_WITH_ID =
        Comparator.comparing(m -> ((String) m.get(ID)));

    private static final Comparator<Map<String, Object>> COMPARING_GROUP_MEMBERS =
        Comparator.comparing(m -> ((String) m.get(VALUE)));

    private static final String AUTHORIZATION = "Basic WFdpa2kuU2NpbUFkbWluOnNjaW1QYXNzd29yZA==";

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";
    private static final String USER_3 = "user3";
    private static final String USER_4 = "user4";

    public static final String RESOURCES = "Resources";

    private static Map<String, Object> getUser(String userName, String displayName, String externalId)
    {
        Map<String, Object> user = new HashMap<>(getResource(USER, displayName, externalId));
        user.put("userName", userName);
        return user;
    }

    private static Map<String, Object> getGroup(String groupName, String externalId,
        List<Map<String, Object>> members)
    {
        Map<String, Object> group = new HashMap<>(getResource(GROUP, groupName, externalId));
        List<Map<String, Object>> memberships = members.stream().map(ScimRestIT::getMember).toList();
        group.put(MEMBERS, memberships);
        return group;
    }

    private static Map<String, Object> getMember(Map<String, Object> m)
    {
        String type = ((String) ((List<?>) m.get(SCHEMAS)).get(0)).endsWith(GROUP) ? GROUP : USER;
        return Map.of(
            VALUE, m.get(ID),
            "type", type,
            "$ref", "/xwiki/rest/scim/v2/" + type + "s/" + m.get(ID),
            "display", m.getOrDefault("userName", m.get("displayName"))
        );
    }

    private static Map<String, Object> getResource(String resourceType, String displayName, String externalId)
    {
        return Map.of(
            SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + resourceType),
            "displayName", displayName,
            "externalId", externalId,
            "meta", Map.of(
                "resourceType", resourceType,
                "created", "2010-01-23T04:56:22Z",
                "lastModified", "2011-05-13T04:42:34Z"
            )
        );
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
        String userId = assertCreateUserOK(setup, NEW_USER, SCIM_ADMIN);

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
        userResourceURL = StringUtils.removeEnd(setup.rest().getBaseURL(), "rest")
                + StringUtils.removeStart(userResourceURL, setup.getBaseURL());
        GetMethod getAsAdmin = executeGet(userResourceURL, SCIM_ADMIN);
        assertOK(getAsAdmin);
        GetMethod getAsAdmin2 = executeGet(userResourceURL, SCIM_ADMIN2);
        assertForbidden(getAsAdmin2);
    }

    private static Map<String, Object> setId(Map<String, ?> resource, String id)
    {
        Map<String, Object> r = new HashMap<>(resource);
        r.put(ID, id);
        return r;
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
        String group1Id = (String) objs.group1().get(ID);
        assertDeleteGroupNotFound(setup, group1Id, SCIM_ADMIN2);

        // Trying to delete a user from the same SCIM admin succeeds
        assertDeleteGroupOK(setup, (String) objs.subgroup().get(ID), SCIM_ADMIN);
        assertFalse(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));

        // We just removed subgroup, so it's not a member of group1 anymore
        removeGroupMembers(objs.group1(), List.of((String) objs.subgroup().get(ID)));

        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, objs.group1(), "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, GROUP1)));
        assertCorrectRightsOnResourceDocument(setup, group1Id, GROUP);

        assertDeleteGroupOK(setup, group1Id, SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user1().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user2().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user3().get(ID), SCIM_ADMIN);
        assertDeleteUserOK(setup,  (String) objs.user4().get(ID), SCIM_ADMIN);

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

        Map<String, Object> user1 = getUser(USER_1, "User 1", "externalIdOfUser1");
        Map<String, Object> user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        Map<String, Object> user3 = getUser(USER_3, "User 3", "externalIdOfUser3");
        Map<String, Object> user4 = getUser(USER_4, "User 4", "externalIdOfUser4");

        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN);
        String user2Id = assertCreateUserOK(setup, user2, SCIM_ADMIN);
        String user3Id = assertCreateUserOK(setup, user3, SCIM_ADMIN);
        String user4Id = assertCreateUserOK(setup, user4, SCIM_ADMIN);

        user1 = setId(user1, user1Id);
        user2 = setId(user2, user2Id);
        user3 = setId(user3, user3Id);
        user4 = setId(user4, user4Id);

        Map<String, Object> subgroup = getGroup(SUB_GROUP, "subgroupExternalId", List.of(user3, user4));
        String subgroupId = assertCreateGroupOK(setup, subgroup, SCIM_ADMIN);
        subgroup = setId(subgroup, subgroupId);
        assertGetGroupOK(setup, subgroupId, SCIM_ADMIN, subgroup, "1");
        assertTrue(setup.rest().exists(new DocumentReference(XWIKI_WIKI, XWIKI_SPACE, SUB_GROUP)));
        Map<String, Object> group1 = getGroup(GROUP1, "group1externalId",
            List.of(user1, user2, user3, subgroup));
        String group1Id = assertCreateGroupOK(setup, group1, SCIM_ADMIN);
        group1 = setId(group1, group1Id);
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, group1, "1");
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
        PatchMethod patchMethod = executePatch(group1URL, SCIM_ADMIN,
            Map.of(
                SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + "PatchOp"),
                "Operations", List.of(
                    Map.of(
                        "op", "remove",
                        "path", "members["
                            + "value eq " + new JsonMapper().writeValueAsString(objs.user1().get(ID))
                            + " or "
                            + "display eq \"user2\""
                            + "]"
                    ),
                    Map.of(
                        "op", "remove",
                        "path", "members["
                            + "value eq " + new JsonMapper().writeValueAsString(objs.subgroup().get(ID))
                            + "]"
                    )
            )
        ));
        assertOK(patchMethod);
        HashMap<String, Object> patchedGroup1 = new HashMap<>(objs.group1());
        removeGroupMembers(patchedGroup1, List.of(
            (String) objs.subgroup().get(ID),
            (String) objs.user1().get(ID),
            (String) objs.user2().get(ID)
        ));
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, "2");

        Map<String, Object> user2Member = getMember(objs.user2());
        patchMethod = executePatch(group1URL, SCIM_ADMIN,
            Map.of(
                SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + "PatchOp"),
                "Operations", List.of(
                    Map.of(
                        "op", "add",
                        "path", MEMBERS,
                        "value", List.of(user2Member)
                    )
                )
            ));
        assertOK(patchMethod);
        ((List<Object>) patchedGroup1.get(MEMBERS)).add(user2Member);
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, "3");

        Map<String, Object> user1Member = getMember(objs.user1());
        Map<String, Object> user3Member = getMember(objs.user3());

        patchMethod = executePatch(group1URL, SCIM_ADMIN,
            Map.of(
                SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + "PatchOp"),
                "Operations", List.of(
                    Map.of(
                        "op", "replace",
                        "path", MEMBERS,
                        "value", List.of(user1Member, user3Member)
                    )
                )
            ));
        assertOK(patchMethod);
        patchedGroup1.put(MEMBERS, List.of(user1Member, user3Member));
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, "4");

        patchMethod = executePatch(group1URL, SCIM_ADMIN,
            Map.of(
                SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + "PatchOp"),
                "Operations", List.of(
                    Map.of(
                        "op", "remove",
                        "path", MEMBERS
                    )
                )
            ));
        assertOK(patchMethod);
        patchedGroup1.put(MEMBERS, List.of());
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, patchedGroup1, "5");

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
        JSONObject actual = getJSONResponse(getMethod);
        Map<String, String> meta = (Map<String, String>) actual.get("meta");
        assertNotNull(meta.remove("created"));
        assertNotNull(meta.remove("lastModified"));
        JSONObject expected = new JSONObject();
        expected.putAll(Map.of(
            "meta", Map.of(
                "resourceType", "User",
                "location", getScimUsersEndpointURL(setup) + "/me",
                "version", "1.1"
            ),
            "id", "me",
            "active", true,
            "displayName", "",
            "userName", "ScimAdmin",
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User")
        ));
        for (Map.Entry<Object, Object> entry : new ArrayList<Map.Entry<Object, Object>>(actual.entrySet())) {
            Object v = entry.getValue();
            if (v instanceof JSONNull || (v instanceof JSONArray a && a.isEmpty())) {
                actual.remove(entry.getKey());
            }
        }
        JSONAssert.assertEquals(expected, actual);
    }

    @Test
    @Order(6)
    void testResourceTypes(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        JSONObject expectedGroupResource = getResourceType(setup, "Group");
        JSONObject expectedUserResource = getResourceType(setup, "User");

        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes", SCIM_ADMIN);
        JSONArray expectedResources = new JSONArray();
        expectedResources.addAll(List.of(expectedGroupResource, expectedUserResource));
        JSONObject expected = new JSONObject();
        expected.putAll(Map.of(
            "totalResults", 2,
            "startIndex", 1,
            "itemsPerPage", 2,
            "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
            RESOURCES, expectedResources
        ));
        JSONObject actual = getJSONResponse(getMethod);
        ((List<Map<String, Object>>) actual.get(RESOURCES)).sort(COMPARING_WITH_ID);
        JSONAssert.assertEquals(expected, actual);

        getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes/Group", SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        JSONAssert.assertEquals(expectedGroupResource, actual);

        getMethod = executeGet(getScimEndpointURL(setup) + "ResourceTypes/User", SCIM_ADMIN);
        actual = getJSONResponse(getMethod);
        JSONAssert.assertEquals(expectedUserResource, actual);
    }

    @Test
    @Order(7)
    void testSchemas(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName());
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "Schemas", SCIM_ADMIN);

        JSONObject expected = new JSONObject();
        expected.putAll(Map.of(
            "totalResults",	2,
            "startIndex",	1,
            "itemsPerPage",	2,
            "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse")
        ));

        JSONObject actual = getJSONResponse(getMethod);

        List<Map<String, Object>> actualResources = (List<Map<String, Object>>) actual.remove(RESOURCES);
        actualResources.sort(COMPARING_WITH_ID);
        assertNotNull(actualResources);
        assertEquals(2, actualResources.size());
        JSONAssert.assertEquals(expected, actual);

        JSONObject groupSchema = (JSONObject) actualResources.get(0);
        assertSchemaOK(setup, "Group", groupSchema);

        JSONObject userSchema = (JSONObject) actualResources.get(1);
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

        Map<String, Object> user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN);
        user1 = setId(user1, user1Id);

        Map<String, Object> user2 = getUser(USER_2, "User 2", "externalIdOfUser2");
        String user2Id = assertCreateUserOK(setup, user2, SCIM_ADMIN);
        user2 = setId(user2, user2Id);

        Map<String, Object> group1 = getGroup(GROUP1, "group1externalId", List.of(user1));
        String group1Id = assertCreateGroupOK(setup, group1, SCIM_ADMIN);
        Map<String, Object> updatedUser = new HashMap<>(user1);
        updatedUser.put("displayName", "Renamed User");
        updatedUser.put("externalId", "renamedUserExternalId");
        updatedUser.put("phoneNumbers", List.of(
            Map.of(
                "value", "+33123456789",
                "type", "work"
            )
        ));

        assertPutUserOK(setup, user1Id, SCIM_ADMIN, updatedUser, "2");
        Map<String, Object> updatedGroup = getGroup(GROUP1, "renamedGroupExternalId", List.of(user2));
        assertPutGroupOK(setup, group1Id, SCIM_ADMIN, updatedGroup, "2");
        assertGetGroupOK(setup, group1Id, SCIM_ADMIN, updatedGroup, "2");

        updatedUser.put("userName", "renamedUser");

        // Renaming a resource fails
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + user1Id, SCIM_ADMIN, updatedUser);
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, putMethod.getStatusCode(), getHttpMethodInfo(putMethod));

        assertDeleteGroupOK(setup, group1Id, SCIM_ADMIN);
        assertDeleteUserOK(setup, user1Id, SCIM_ADMIN);
    }

    @Test
    @Order(9)
    void testUserPatch(TestUtils setup) throws Exception
    {
        setScimAdmins(setup, XWIKIDOT + SCIM_ADMIN.userName() + ',' + XWIKIDOT + SCIM_ADMIN2.userName());
        Map<String, Object> user1 = getFullUser1();
        String user1Id = assertCreateUserOK(setup, user1, SCIM_ADMIN);
        setId(user1, user1Id);

        String user1URL = getScimUsersEndpointURL(setup) + "/" + user1Id;
        PatchMethod patchMethod = executePatch(user1URL, SCIM_ADMIN,
            Map.of(
                SCHEMAS, List.of(SCIM_SCHEMA_PREFIX + "PatchOp"),
                "Operations", List.of(
                    Map.of(
                        "op", "add",
                        "path", "name.familyName",
                        "value", "New Family Name"
                    ),
                    Map.of(
                        "op", "remove",
                        "path", "ims[type eq \"aim\"]"
                    ),
                    Map.of(
                        "op", "add",
                        "path", "active",
                        "value", false
                    )
                )
            ));
        assertOK(patchMethod);
        user1.remove("ims");
        user1.put("active", false);
        Map<String, String> newName = new HashMap<>((Map<String, String>) user1.get("name"));
        newName.put("familyName", "New Family Name");
        user1.put("name", newName);
        assertGetUserOK(setup, user1Id, SCIM_ADMIN, user1, "2");
        assertDeleteUserOK(setup, user1Id, SCIM_ADMIN);
    }

    @Test
    @Order(9)
    void testServiceProviderConfig(TestUtils setup) throws Exception
    {
        GetMethod getMethod = executeGet(getScimEndpointURL(setup) + "ServiceProviderConfig", SCIM_ADMIN);
        assertOK(getMethod);
        JSONObject actual = getJSONResponse(getMethod);
        JSONArray authenticationSchemes = (JSONArray) actual.get("authenticationSchemes");
        assertNotNull(authenticationSchemes);
        Set<String> types = ((List<Map<String, String>>) authenticationSchemes)
            .stream()
            .map(as -> as.get("type"))
            .collect(Collectors.toSet());
        assertEquals(Set.of("oauth2", "httpbasic"), types);
    }

    private static @NotNull Map<String, Object> getFullUser1()
    {
        Map<String, Object> user1 = getUser(USER_1, "Babs Jensen", "externalIdOfUser1");
        user1.put("nickName", "Babs");
        user1.put("title", "Tour Guide");
        user1.put("userType", "Intern");
        user1.put("preferredLanguage", "da, en-gb;q=0.8, en;q=0.7");
        user1.put("locale", "fr");
        user1.put("timezone", "America/Los_Angeles");
        user1.put("emails", List.of(
            Map.of(
                "value", "bjensen@example.com",
                "type", "work",
                "primary", true
            ),
            Map.of(
                "value", "babs@jensen.org",
                "type", "home"
            )));
        user1.put("phoneNumbers", List.of(
            Map.of(
                "value", "555-555-5555",
                "type", "work"
            ),
            Map.of(
                "value", "555-555-4444",
                "type", "mobile"
            )
        ));
        user1.put("ims", List.of(
            Map.of(
                "value", "someaimhandle",
                "type", "aim"
            )
        ));
        user1.put("photos", List.of(
            Map.of(
                "value", "https://photos.example.com/profilephoto/72930000000Ccne/F",
                "type", "photo"
            ),
            Map.of(
                "value", "https://photos.example.com/profilephoto/72930000000Ccne/T",
                "type", "thumbnail"
            )
        ));
        user1.put("addresses", List.of(
            Map.of(
                "type", "work",
                "streetAddress", "100 Universal City Plaza",
                "locality", "Hollywood",
                "region", "CA",
                "postalCode", "91608",
                "country", "USA",
                "formatted", "100 Universal City Plaza\nHollywood, CA 91608 USA",
                "primary", true
            ), Map.of(
                "type", "home",
                "streetAddress", "456 Hollywood Blvd",
                "locality", "Hollywood",
                "region", "CA",
                "postalCode", "91608",
                "country", "USA",
                "formatted", "456 Hollywood Blvd\nHollywood, CA 91608 USA"
            )
        ));
        user1.put("name", Map.of(
            "formatted", "Ms. Barbara J Jensen, III",
            "familyName", "Jensen",
            "givenName", "Barbara",
            "middleName", "Jane",
            "honorificPrefix", "Ms.",
            "honorificSuffix", "III"
        ));
        user1.put("profileUrl", "https://login.example.com/bjensen");
        return user1;
    }

    private static JSONObject getResourceType(TestUtils setup, String resourceType)
    {
        JSONObject expectedResourceType = new JSONObject();
        expectedResourceType.putAll(Map.of(
            "meta", Map.of(
                "resourceType", "ResourceType",
                "location", getScimEndpointURL(setup) + "ResourceTypes/" + resourceType
            ),
            "id", resourceType,
            "name", resourceType,
            "description", "Top level Scim" + resourceType,
            "endpoint", "/" + resourceType + 's',
            "schemaExtensions", List.of(),
            "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
            "schema", "urn:ietf:params:scim:schemas:core:2.0:" + resourceType
        ));
        return expectedResourceType;
    }

    private static void assertSchemaOK(TestUtils setup, String resourceType, JSONObject actual)
    {
        String id = "urn:ietf:params:scim:schemas:core:2.0:" + resourceType;
        JSONObject expectedResourceSchema = new JSONObject();
        expectedResourceSchema.putAll(Map.of(
            "meta", Map.of(
                "resourceType", "Schema",
                "location", getScimEndpointURL(setup) + "Schemas/" + id
            ),
            "id", id,
            "name", resourceType,
            "description", "Top level Scim" + resourceType
        ));
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) actual.remove("attributes");
        assertAttributesOK(attributes);

        JSONAssert.assertEquals(expectedResourceSchema, actual);
    }

    private static void assertAttributesOK(List<Map<String, Object>> attributes)
    {
        assertJSONNotNull(attributes);
        assertFalse(attributes.isEmpty());
        for (Map<String, Object> attribute : attributes) {
            Object subAttributes = attribute.remove("subAttributes");
            Object canonicalValues = attribute.remove("canonicalValues");
            Object referenceTypes = attribute.remove("referenceTypes");
            String type = (String) attribute.get("type");
            switch (type) {
                case "reference":
                    assertJSONNotNull(referenceTypes);
                    assertFalse(((List) referenceTypes).isEmpty());
                    for (String referenceType : (List<String>) referenceTypes) {
                        assertTrue(List.of("external", "User", "Group").contains(referenceType));
                    }
                    assertJSONNull(canonicalValues);
                    assertJSONNull(subAttributes);
                    break;
                case "complex":
                    assertJSONNull(referenceTypes);
                    assertJSONNull(canonicalValues);
                    assertJSONNotNull(subAttributes);
                    assertAttributesOK((List<Map<String, Object>>) subAttributes);
                    break;
                default:
                    assertJSONNull(referenceTypes);
                    if ("type".equals(attribute.get("name"))) {
                        assertEquals("string", type);
                        if (canonicalValues != null && !(canonicalValues instanceof JSONNull)) {
                            assertFalse(((List) canonicalValues).isEmpty());
                        }
                    } else {
                        assertJSONNull(canonicalValues);
                    }
                    assertJSONNull(subAttributes);
            }
            assertEquals(SCHEMA_ATTRIBUTES, attribute.keySet());
        }
    }

    private static void removeGroupMembers(Map<String, Object> group1, List<String> membersToRemove)
    {
        group1.put(MEMBERS,
            new ArrayList<>(((List<?>) group1.get(MEMBERS))
                .stream()
                .filter(m -> !membersToRemove.contains((String) ((Map<?, ?>) m).get(VALUE)))
                .toList())
        );
    }

    private String assertCreateUserOK(TestUtils setup, Map<String, Object> newUser, XWikiUser scimAdmin) throws Exception
    {
        PostMethod postMethod = executePost(getScimUsersEndpointURL(setup), scimAdmin, newUser);
        assertOK(postMethod);
        return assertUserEqual(newUser, postMethod, "1");
    }

    private void assertPutUserOK(TestUtils setup, String userId, XWikiUser scimAdmin, Map<String, Object> putUser,
        String version) throws Exception
    {
        PutMethod putMethod = executePut(getScimUsersEndpointURL(setup) + "/" + userId, scimAdmin, putUser);
        assertOK(putMethod);
        assertUserEqual(putUser, putMethod, version);
    }

    private void assertPutGroupOK(TestUtils setup, String groupId, XWikiUser scimAdmin, Map<String, Object> putGroup,
        String version) throws Exception
    {
        PutMethod putMethod = executePut(getScimGroupsEndpointURL(setup) + "/" + groupId, scimAdmin, putGroup);
        assertOK(putMethod);
        assertGroupEqual(putGroup, putMethod, version);
    }

    private String assertUserEqual(Map<String, Object> userSpec, HttpMethodBase method, String version) throws IOException
    {
        return assertUserEqual(userSpec, getJSONResponse(method), version);
    }

    private String assertGroupEqual(Map<String, Object> groupSpec, HttpMethodBase method, String version) throws IOException
    {
        return assertGroupEqual(groupSpec, getJSONResponse(method), version);
    }

    private static @NotNull String assertUserEqual(Map<String, ?> userSpec, JSONObject actual, String version)
    {
        JSONObject expected = new JSONObject();
        Object activeObj = userSpec.get("active");
        boolean active = activeObj == null || activeObj.equals(true);
        assertJSONNotNull(actual.remove("groups"));
        Map<?, ?> receivedMeta = (Map<?, ?>) actual.get("meta");
        assertJSONNotNull(receivedMeta.remove("created"));
        assertJSONNotNull(receivedMeta.remove("lastModified"));
        String receivedId = (String) actual.get(ID);
        if (!actual.has("active")) {
            actual.put("active", true);
        }
        expected.putAll(Map.of(
            "meta", Map.of(
                "resourceType", USER,
                "location", "http://localhost:8080/xwiki/rest/scim/v2/Users/" + receivedId,
                "version", version + ".1"
            ),
            ID, receivedId,
            "externalId", userSpec.get("externalId"),
            "active", active,
            "displayName", ObjectUtils.defaultIfNull(userSpec.get("displayName"), ""),
            "userName", userSpec.get("userName"),
            SCHEMAS, List.of("urn:ietf:params:scim:schemas:core:2.0:User")
        ));

        expected.put("userType", userSpec.get("userType"));
        expected.put("profileUrl", userSpec.get("profileUrl"));
        expected.put("nickName", userSpec.get("nickName"));

        keepLists(expected, userSpec, "phoneNumbers", "addresses", "ims", "emails", "photos");
        keepSingle(expected, userSpec, "title", "timezone");
        expected.put("name", userSpec.get("name"));
        keepLocale(expected, userSpec);
        for (String key : List.of(
            "entitlements", "nickName", "password", "photos", "profileUrl", "roles", "userType",
            "x509Certificates", "addresses", "emails", "ims", "locale", "name", "phoneNumbers", "preferredLanguage",
            "timezone", "title"
        )) {
            Object v = actual.get(key);
            if (v == null || v instanceof JSONNull || (v instanceof JSONArray a && a.isEmpty())) {
                actual.remove(key);
            }
        }

        JSONAssert.assertEquals(expected, actual);
        return receivedId;
    }

    private static void keepLocale(JSONObject expected, Map<String, ?> userSpec)
    {
        // simulate the limitations of the current implementation
        String locale = (String) userSpec.get("preferredLanguage");
        if (locale == null) {
            locale = (String) userSpec.get("locale");
        }
        if (locale != null) {
            String r = Locale.LanguageRange.parse(locale).get(0).getRange();
            expected.put("preferredLanguage", r);
            expected.put("locale", r);
        }
    }

    private static void oldKeepName(JSONObject expected, Map<String, ?> userSpec)
    {
        // XWiki has limited support for name details
        Map<String, String> name = (Map<String, String>) userSpec.get("name");
        if (name != null) {
            HashMap<String, Object> expectedName = new HashMap<>(2);
            String givenName = name.get("givenName");
            expectedName.put("givenName", givenName == null ? JSONNull.getInstance() : givenName);
            String familyName = name.get("familyName");
            expectedName.put("familyName", familyName == null ? JSONNull.getInstance() : familyName);
            expectedName.put("middleName", JSONNull.getInstance());
            expectedName.put("honorificPrefix", JSONNull.getInstance());
            expectedName.put("honorificSuffix", JSONNull.getInstance());
            expectedName.put("formatted", JSONNull.getInstance());
            expected.put("name", expectedName);
        }
    }

    private static void keepSingle(JSONObject expected, Map<String,?> userSpec, String... fields)
    {
        for (String field : fields) {
            Object v = userSpec.get(field);
            if (v != null) {
                expected.put(field, v);
            }
        }
    }

    private static void keepLists(JSONObject expected, Map<String, ?> userSpec, String... fields)
    {
        // Our implementation currently only keeps the primary value of list fields
        // this method reflects this in expected values
        for (String field : fields) {
            List<Map<String, Object>> fullSpec = (List<Map<String, Object>>) userSpec.get(field);
            if (fullSpec == null) {
                continue;
            }

            expected.put(field, fullSpec.stream().map(m -> {
                if (m.containsKey("primary")) {
                    return m;
                }
                Map<String, Object> res = new HashMap<>(m);
                res.put("primary", false);
                return res;
            }).toList());
        }
    }

    private static void oldKeepPrimary(JSONObject expected, String field, List<Map<String, Object>> fullSpec)
    {
        Map<String, Object> val = null;
        for (Map<String, Object> v : fullSpec) {
            if (Boolean.TRUE.equals(v.get("primary"))) {
                val = v;
                break;
            }
            if (val == null) {
                val = v;
            }
        }

        if (val != null) {
            // we don't keep the phone number's type, most address fields and the unique value is always primary
            val = new HashMap<>(val);
            if (!"ims".equals(field)) {
                val.put("type", JSONNull.getInstance());
            }
            if ("addresses".equals(field)) {
                val.put("streetAddress", JSONNull.getInstance());
                val.put("postalCode", JSONNull.getInstance());
                val.put("region", JSONNull.getInstance());
            }
            val.put("display", JSONNull.getInstance());
            val.put("primary", true);
            expected.put(field, List.of(val));
        }
    }

    private static @NotNull String assertGroupEqual(Map<String, Object> groupSpec, JSONObject actual, String version)
    {
        JSONObject expected = new JSONObject();
        Object activeObj = groupSpec.get("active");
        boolean active = activeObj == null || activeObj.equals(true);
        actual.remove("timezone");
        Map<?, ?> receivedMeta = (Map<?, ?>) actual.get("meta");
        assertJSONNotNull(receivedMeta.remove("created"));
        assertJSONNotNull(receivedMeta.remove("lastModified"));
        String receivedId = (String) actual.get(ID);
        if (!actual.has("active")) {
            actual.put("active", true);
        }
        expected.putAll(Map.of(
            "meta", Map.of(
                "resourceType", GROUP,
                "location", "http://localhost:8080/xwiki/rest/scim/v2/Groups/" + receivedId,
                "version", version + ".1"
            ),
            ID, receivedId,
            "externalId", groupSpec.get("externalId"),
            "active", active,
            "displayName", groupSpec.get("displayName"),
            SCHEMAS, List.of("urn:ietf:params:scim:schemas:core:2.0:Group")
        ));
        if (actual.has(MEMBERS)) {
            List<Map<String, Object>> specMembers = new ArrayList<>((List<Map<String, Object>>) groupSpec.get(MEMBERS));
            specMembers.sort(COMPARING_GROUP_MEMBERS);
            groupSpec.put(MEMBERS, specMembers);
            ((List<Map<String, Object>>) actual.get(MEMBERS)).sort(COMPARING_GROUP_MEMBERS);
            expected.put(MEMBERS, specMembers);
        }
        JSONAssert.assertEquals(expected, actual);
        return receivedId;
    }

    private static void assertJSONNull(Object v)
    {
        if (v != null) {
            assertInstanceOf(JSONNull.class, v);
        }
    }

    private static void assertJSONNotNull(Object v)
    {
        assertNotNull(v);
        assertFalse(v instanceof JSONNull);
    }

    private String assertCreateGroupOK(TestUtils setup, Map<String, Object> newGroup, XWikiUser scimAdmin)
        throws Exception
    {
        PostMethod postMethod = executePost(getScimGroupsEndpointURL(setup), scimAdmin, newGroup);
        assertOK(postMethod);
        return assertGroupEqual(newGroup, postMethod, "1");
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

    private void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, Map<String, Object> userSpec)
        throws Exception
    {
        assertGetUserOK(setup, id, scimAdmin, userSpec, "1");
    }

    private void assertGetUserOK(TestUtils setup, String id, XWikiUser scimAdmin, Map<String, Object> userSpec,
        String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertUserEqual(userSpec, getMethod, version);
    }

    private void assertGetUserNotFound(TestUtils setup, String id, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMUserURL(setup, id), scimAdmin);
        assertNotFound(getMethod);
    }

    private static @NotNull String getSCIMUserURL(TestUtils setup, String id)
    {
        return getScimUsersEndpointURL(setup) + "/" + id;
    }

    private void assertGetGroupOK(TestUtils setup, String id, XWikiUser scimAdmin, Map<String, Object> groupSpec,
        String version) throws Exception
    {
        GetMethod getMethod = executeGet(getSCIMGroupURL(setup, id), scimAdmin);
        assertOK(getMethod);
        assertGroupEqual(groupSpec, getMethod, version);
    }

    private static @NotNull String getSCIMGroupURL(TestUtils setup, String id)
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

    private void assertUsersListEquals(TestUtils setup, List<Map<String, ?>> expectedUsers, XWikiUser scimAdmin) throws Exception
    {
        GetMethod getMethod = executeGet(getScimUsersEndpointURL(setup), scimAdmin);
        assertOK(getMethod);
        JSONObject expected = getListResponseJSONObject(expectedUsers.size());
        JSONObject actual = getJSONResponse(getMethod);
        Object resourcesObj = actual.remove(RESOURCES);
        assertEqualsIgnoreNulls(expected, actual);
        List<?> resources;
        if (resourcesObj == null || resourcesObj instanceof JSONNull) {
            resources = List.of();
        } else if (resourcesObj instanceof List<?> resourceList) {
            resources = resourceList;
        } else {
            fail();
            // unreachable, but this is to make null checkers happy
            resources = List.of();
        }
        assertEquals(expectedUsers.size(), resources.size());
        for (int i = 0; i < expectedUsers.size(); i++) {
            assertUserEqual(expectedUsers.get(i), (JSONObject) resources.get(i), "1");
        }
    }

    private static void setScimAdmins(TestUtils setup, String users)
    {
        setup.updateObject(SCIM_CODE, CONFIGURATION, "SCIM.Code.ConfigurationClass", 0,
            "users", users, "advertisedAuthSchemes", "oauth2,httpbasic");

    }

    private static void assertZeroResults(GetMethod getMethod) throws IOException
    {
        JSONObject expected = getListResponseJSONObject(0);
        JSONObject actual = getJSONResponse(getMethod);
        assertEqualsIgnoreNulls(expected, actual);
    }

    private static @NotNull JSONObject getListResponseJSONObject(int totalResults)
    {
        JSONObject expected = new JSONObject();
        expected.putAll(Map.of(
            "totalResults", totalResults,
            "startIndex", totalResults == 0 ? JSONNull.getInstance() : 1,
            "itemsPerPage", totalResults == 0 ? JSONNull.getInstance() : totalResults,
            SCHEMAS, List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse")
        ));
        return expected;
    }

    private static void assertEqualsIgnoreNulls(JSONObject expected, JSONObject actual)
    {
        for (Object key : List.copyOf(actual.keySet())) {
            if (actual.get(key) instanceof JSONNull) {
                actual.remove(key);
            }
        }

        for (Object key : List.copyOf(expected.keySet())) {
            if (expected.get(key) == null || expected.get(key) instanceof JSONNull) {
                expected.remove(key);
            }
        }
        JSONAssert.assertEquals(expected, actual);
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
    class ScimpleSchemasIT extends org.apache.directory.scim.compliance.tests.SchemasIT
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