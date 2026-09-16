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

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

@UITest(vnc = false)
//@UITest(vnc = false, servletEngine = org.xwiki.test.docker.junit5.servletengine.ServletEngine.EXTERNAL)
public class MakeMSValidatorHappyIT extends AbstractScimHttp
{
    @Test
    void testUsers(TestUtils setup) throws Exception
    {
        JSONObject msUser = putAll(getUser("cletus@langosh.com", "PFMICPFPSOSQ", null),
            "active", true,
            "title", "YXKESECQBFVB",
            "emails", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "scotty_christiansen@rau.ca",
                    "primary", true
                )
            ),
            "preferredLanguage", "fr-CH",
            "name", newJSONObject(
                "givenName", "Enrique",
                "familyName", "Kelli",
                "formatted", "Jaylen",
                "middleName", "Dannie",
                "honorificPrefix", "Leone",
                "honorificSuffix", "Nikita"
            ),
            "addresses", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "formatted", "EEWMSMXSGLCQ",
                    "streetAddress", "337 Glover Prairie",
                    "locality", "BTVOQHXEATZR",
                    "region", "ZIUIBXKJXHZO",
                    "postalCode", "xz14 0at",
                    "primary", true,
                    "country", "Solomon Islands"
                )
            ),
            "phoneNumbers", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "55-838-0679",
                    "primary", true
                ),
                newJSONObject(
                    "type", "mobile",
                    "value", "18-694-9832"
                ),
                newJSONObject(
                    "type", "fax",
                    "value", "18-694-9832"
                )
            ),
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", newJSONObject(
                "employeeNumber", "NSNGIMVXCCHB",
                "department", "GICCWMEWZJJJ",
                "costCenter", "TDTHMCTMZGOG",
                "organization", "PYMINZTAPJFU",
                "division", "QBWCARXIIOZB",
                "manager", newJSONObject("value", "OIKRUCLEDKOA")
            ),
            "roles", newJSONArray(
                newJSONObject(
                    "primary", "True",
                    "display", "TKDNPJZDDPZE",
                    "value", "SPBSYGFGKUGN",
                    "type", "NRSTTBUPFVVA"
                )
            ),
            "userType", "JOFAEKCKXNGD",
            "nickName", "OQHMHNAWNSJZ",
            "locale", "FEDDVSNYMOXI",
            "timezone", "Africa/Cairo",
            "profileUrl", "PEUNKFFNIXHA",
            "schemas", newJSONArray(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
            )
        );

        String userId = assertCreateUserOK(setup, msUser, scimAdmin(), msUser.getString("userName"));
        assertDeleteUserOK(setup, userId, scimAdmin());
    }

    @Test
    void testPatch(TestUtils setup) throws Exception
    {
        String userName = "ernie@hermanblick.info";
        JSONObject msUser = putAll(getUser(userName, "TGYVEMWPLEZL", null),
            "title", "EQDAGIBHTMTA",
            "emails", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "romaine.hand@stiedemannwiza.name",
                    "primary", true
                )
            ),
            "preferredLanguage", "kkj",
            "name", newJSONObject(
                "givenName", "Berta",
                "familyName", "Lorine",
                "formatted" , "Adriel",
                "middleName", "Lyda",
                "honorificPrefix", "Audreanne",
                "honorificSuffix", "Amanda"
            ),
            "addresses", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "formatted", "VPCONTNFHDXT",
                    "streetAddress", "19214 Scarlett Curve",
                    "locality", "ZJORBIEHNLRE",
                    "region", "MXJRWRWHDBUC",
                    "postalCode", "oa30 3bp",
                    "primary", true,
                    "country", "Uzbekistan"
                )
            ),
            "phoneNumbers", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "29-159-6754",
                    "primary", true
                ),
                newJSONObject(
                    "type", "mobile",
                    "value", "29-159-6754"
                ),
                newJSONObject(
                    "type", "fax",
                    "value", "69-790-9907"
                )
            ),
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", newJSONObject(
                "employeeNumber", "FQXZVAGADKYG",
                "department", "ZNDCMNZZENFV",
                "costCenter", "CQUQTELQNFQO",
                "organization", "FBVSYJRZJZMB",
                "division", "HKMHPSOWLVLU",
                "manager", newJSONObject("value", "YWTSXAWWZKML")
            ),
            "roles", newJSONArray(
                newJSONObject(
                    "primary", "True",
                    "display", "LBSTKXVQDCRV",
                    "value", "AUGSFQISUVTB",
                    "type", "RZZXHTYAMEOB"
                )
            ),
            "userType", "UVDCEBMHEBMH",
            "nickName", "WBNKKMYKBWST",
            "locale", "NNAUQWGUBHNM",
            "timezone", "America/Asuncion",
            "profileUrl", "NVTWWTNJEAGJ",
            "schemas", newJSONArray(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
            )
        );
        String userId = assertCreateUserOK(setup, msUser, scimAdmin(), msUser.getString("userName"));

        executePatch(getSCIMUserURL(setup, userId), scimAdmin(), newJSONObject(
            "schemas", newJSONArray("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
            "Operations", newJSONArray(
                newJSONObject(
                    "op", "replace",
                    "path", "emails[type eq \"work\"].value",
                    "value", "stanford_abshire@pouros.biz"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "emails[type eq \"work\"].primary",
                    "value", true
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].formatted",
                    "value", "WOAITZBZRAHK"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].streetAddress",
                    "value", "92033 Mariah Run"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].locality",
                    "value", "BKTZDFEECYOG"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].region",
                    "value", "KJFSIXLXHERG"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].postalCode",
                    "value", "fl8 1qc"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].primary",
                    "value", true
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "addresses[type eq \"work\"].country",
                    "value", "Haiti"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "phoneNumbers[type eq \"work\"].value",
                    "value", "15-338-2012"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "phoneNumbers[type eq \"work\"].primary",
                    "value", true
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "phoneNumbers[type eq \"mobile\"].value",
                    "value", "15-338-2012"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "phoneNumbers[type eq \"fax\"].value",
                    "value", "15-338-2012"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "roles[primary eq \"True\"].display",
                    "value", "JGMCKLRHCOKM"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "roles[primary eq \"True\"].value",
                    "value", "JYQFMDMKMSCZ"
                ),
                newJSONObject(
                    "op", "replace",
                    "path", "roles[primary eq \"True\"].type",
                    "value", "QCNEYSNLJOHO"
                ),
                newJSONObject(
                    "op", "replace",
                    "value", newJSONObject(
                        "active", true,
                        "displayName", "SIIIGBWBEOQH",
                        "title", "NQUQRLCFYVZP",
                        "preferredLanguage", "es-CR",
                        "name.givenName", "Eleazar",
                        "name.familyName", "June",
                        "name.formatted", "Annie",
                        "name.middleName", "Erling",
                        "name.honorificPrefix", "Tyreek",
                        "name.honorificSuffix", "Kaylah",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber", "NAFXTHLVJDBU",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department", "HOVVFZLLOTKT",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:costCenter", "WLIXSVGDBBJC",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:organization", "MGMWRCMGMYZY",
                        "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:division", "UWXGFAMCRFVV",
                        "userType", "RYRLEIUWLTEP",
                        "nickName", "OZWICYAMTSJJ",
                        "locale", "JVQRAREQXARV",
                        "timezone", "America/Catamarca",
                        "profileUrl", "RSQBLHFSNQLQ"
                    )
                )
            )
        ));

        JSONObject expectedUser = putAll(getUser(userName, "SIIIGBWBEOQH", null),
            "title", "NQUQRLCFYVZP",
            "emails", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "stanford_abshire@pouros.biz",
                    "primary", true
                )
            ),
            "preferredLanguage", "es-CR",
            "name", newJSONObject(
                "givenName", "Eleazar",
                "familyName", "June",
                "formatted" , "Annie",
                "middleName", "Erling",
                "honorificPrefix", "Tyreek",
                "honorificSuffix", "Kaylah"
            ),
            "addresses", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "formatted", "WOAITZBZRAHK",
                    "streetAddress", "92033 Mariah Run",
                    "locality", "BKTZDFEECYOG",
                    "region", "KJFSIXLXHERG",
                    "postalCode", "fl8 1qc",
                    "primary", true,
                    "country", "Haiti"
                )
            ),
            "phoneNumbers", newJSONArray(
                newJSONObject(
                    "type", "work",
                    "value", "15-338-2012",
                    "primary", true
                ),
                newJSONObject(
                    "type", "mobile",
                    "value", "15-338-2012"
                ),
                newJSONObject(
                    "type", "fax",
                    "value", "15-338-2012"
                )
            ),
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", newJSONObject(
                "employeeNumber", "NAFXTHLVJDBU",
                "department", "HOVVFZLLOTKT",
                "costCenter", "WLIXSVGDBBJC",
                "organization", "MGMWRCMGMYZY",
                "division", "UWXGFAMCRFVV",
                "manager", newJSONObject("value", "YWTSXAWWZKML")
            ),
            "roles", newJSONArray(
                newJSONObject(
                    "primary", "True",
                    "display", "JGMCKLRHCOKM",
                    "value", "JYQFMDMKMSCZ",
                    "type", "QCNEYSNLJOHO"
                )
            ),
            "userType", "RYRLEIUWLTEP",
            "nickName", "OZWICYAMTSJJ",
            "locale", "JVQRAREQXARV",
            "timezone", "America/Catamarca",
            "profileUrl", "RSQBLHFSNQLQ",
            "schemas", newJSONArray(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
            )
        );
        assertGetUserOK(setup, userId, scimAdmin(), expectedUser);
    }
}
